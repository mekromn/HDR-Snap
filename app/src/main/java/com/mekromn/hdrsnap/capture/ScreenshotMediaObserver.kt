package com.mekromn.hdrsnap.capture

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.mekromn.hdrsnap.HdrSnapPrefs
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ScreenshotMediaObserver(private val context: Context) {
    private val resolver = context.contentResolver
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val processor = ScreenshotProcessor(context)
    private val prefs = HdrSnapPrefs(context)
    private val running = AtomicBoolean(false)

    private val completed = LinkedHashSet<Long>()
    private val pending = LinkedHashMap<Long, PendingScreenshot>()
    private val serviceStartedAtMs = System.currentTimeMillis()

    private var editorActive = false
    private var editorSessionStartMs = 0L
    private var editorExitHoldUntilMs = 0L

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            if (prefs.autoProcessScreenshots) scheduleScan(250L)
        }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        resolver.registerContentObserver(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            true,
            observer
        )
        scheduler.execute { seedExistingScreenshots() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        resolver.unregisterContentObserver(observer)
        scheduler.shutdownNow()
    }

    /** Manual diagnostic action. Automatic processing does not require this. */
    fun processLatest() {
        scheduleScan(0L, forceNewest = true)
    }

    /**
     * Called by the AccessibilityService for TYPE_WINDOW_STATE_CHANGED events.
     * Pixel Studio and the legacy Markup editor are treated as editing sessions, so a
     * screenshot is never replaced while the user is still editing it.
     */
    fun onWindowStateChanged(packageName: String?, className: String?) {
        if (!running.get()) return
        scheduler.execute {
            val now = System.currentTimeMillis()
            val isEditor = isScreenshotEditor(packageName, className)

            if (isEditor) {
                if (!editorActive) editorSessionStartMs = now
                editorActive = true
                editorExitHoldUntilMs = Long.MAX_VALUE
                return@execute
            }

            if (editorActive && !isTransientWindow(packageName)) {
                editorActive = false
                // Give Pixel Studio / Markup time to publish or finish rewriting the final URI.
                editorExitHoldUntilMs = now + POST_EDITOR_GRACE_MS
                scheduleScan(500L)
                scheduleDrain(POST_EDITOR_GRACE_MS + QUIET_AFTER_CHANGE_MS)
            }
        }
    }

    private fun scheduleScan(delayMs: Long, forceNewest: Boolean = false) {
        if (!running.get()) return
        scheduler.schedule({
            if (!running.get()) return@schedule
            scanForCandidates(forceNewest)
            scheduleDrain(500L, forceNewest)
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun scheduleDrain(delayMs: Long, forceNewest: Boolean = false) {
        if (!running.get()) return
        scheduler.schedule({ drainPending(forceNewest) }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun seedExistingScreenshots() {
        queryRecentScreenshots(limit = 64).forEach { row ->
            // Do not accidentally process an old screenshot because some unrelated image
            // changes in MediaStore after the service is enabled.
            if (row.dateAddedMs < serviceStartedAtMs - 1_000L) completed.add(row.id)
        }
    }

    private fun scanForCandidates(forceNewest: Boolean) {
        if (!hasReadPermission()) {
            prefs.lastStatus = "Photo permission required to inspect system screenshots"
            return
        }

        val now = System.currentTimeMillis()
        val rows = queryRecentScreenshots(limit = 32)
        var forced = false

        rows.forEach { row ->
            if (row.ownerPackage == context.packageName || isHdrSnapOutput(row.name)) return@forEach

            if (forceNewest && !forced) {
                completed.remove(row.id)
                forced = true
            } else if (completed.contains(row.id)) {
                return@forEach
            }

            val existing = pending[row.id]
            if (existing == null) {
                val fresh = PendingScreenshot(
                    id = row.id,
                    uri = row.uri,
                    name = row.name,
                    firstSeenMs = now,
                    lastObservedChangeMs = now,
                    dateModifiedSeconds = row.dateModifiedSeconds,
                    sizeBytes = row.sizeBytes
                )
                pending[row.id] = fresh
                maybeLinkEditorReplacement(fresh, now)
            } else if (
                existing.dateModifiedSeconds != row.dateModifiedSeconds ||
                existing.sizeBytes != row.sizeBytes
            ) {
                existing.dateModifiedSeconds = row.dateModifiedSeconds
                existing.sizeBytes = row.sizeBytes
                existing.lastObservedChangeMs = now
            }
        }
    }

    private fun maybeLinkEditorReplacement(fresh: PendingScreenshot, now: Long) {
        val inEditWindow = editorActive || now <= editorExitHoldUntilMs
        if (!inEditWindow) return

        val older = pending.values
            .filter { it.id != fresh.id && !it.superseded && it.firstSeenMs <= fresh.firstSeenMs }
            .maxByOrNull { it.firstSeenMs }
            ?: return

        // If the editor publishes a new MediaStore row instead of rewriting the original,
        // the newer row becomes the source of truth. The old source is only removed after
        // the new row has been converted and gainmap-verified successfully.
        older.superseded = true
        fresh.supersededIds.add(older.id)
        fresh.supersededUris.add(older.uri)
        fresh.supersededIds.addAll(older.supersededIds)
        fresh.supersededUris.addAll(older.supersededUris)
    }

    private fun drainPending(forceNewest: Boolean) {
        if (!running.get()) return
        if (!prefs.autoProcessScreenshots && !forceNewest) return

        val now = System.currentTimeMillis()
        if (editorActive || now < editorExitHoldUntilMs) {
            scheduleDrain(1_000L, forceNewest)
            return
        }

        val candidates = pending.values
            .filter { !it.superseded }
            .sortedBy { it.firstSeenMs }

        var processedAny = false
        for (candidate in candidates) {
            val oldEnough = now - candidate.firstSeenMs >= INITIAL_EDIT_GRACE_MS
            val quietEnough = now - candidate.lastObservedChangeMs >= QUIET_AFTER_CHANGE_MS
            if (!forceNewest && (!oldEnough || !quietEnough)) continue

            val current = queryRow(candidate.id)
            if (current == null) {
                markCompleted(candidate)
                continue
            }

            if (
                current.dateModifiedSeconds != candidate.dateModifiedSeconds ||
                current.sizeBytes != candidate.sizeBytes
            ) {
                candidate.dateModifiedSeconds = current.dateModifiedSeconds
                candidate.sizeBytes = current.sizeBytes
                candidate.lastObservedChangeMs = now
                continue
            }

            val result = processor.process(
                candidate.uri,
                candidate.name,
                candidate.supersededUris
            )

            if (result.success) {
                markCompleted(candidate)
            } else {
                candidate.attempts++
                candidate.lastObservedChangeMs = now
                if (candidate.attempts >= MAX_RETRIES) markCompleted(candidate)
            }
            processedAny = true
            if (forceNewest) break
        }

        if (pending.values.any { !it.superseded }) {
            scheduleDrain(if (processedAny) 750L else 1_250L, false)
        }
    }

    private fun markCompleted(candidate: PendingScreenshot) {
        completed.add(candidate.id)
        candidate.supersededIds.forEach { completed.add(it) }
        while (completed.size > 256) completed.remove(completed.first())

        pending.remove(candidate.id)
        candidate.supersededIds.forEach { pending.remove(it) }
    }

    private fun hasReadPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun queryRecentScreenshots(limit: Int): List<MediaRow> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME
        )

        val result = ArrayList<MediaRow>()
        resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val ownerCol = cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)

            var scanned = 0
            while (cursor.moveToNext() && scanned++ < limit) {
                val name = cursor.getString(nameCol) ?: continue
                val path = cursor.getString(pathCol) ?: ""
                if (!isScreenshot(name, path)) continue

                val id = cursor.getLong(idCol)
                result += MediaRow(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = name,
                    path = path,
                    dateAddedMs = cursor.getLong(addedCol) * 1_000L,
                    dateModifiedSeconds = cursor.getLong(modifiedCol),
                    sizeBytes = cursor.getLong(sizeCol),
                    ownerPackage = if (ownerCol >= 0) cursor.getString(ownerCol) else null
                )
            }
        }
        return result
    }

    private fun queryRow(id: Long): MediaRow? {
        return queryRecentScreenshots(limit = 64).firstOrNull { it.id == id }
    }

    private fun isScreenshot(name: String, path: String): Boolean {
        return name.startsWith("Screenshot", ignoreCase = true) ||
            path.contains("Screenshots", ignoreCase = true)
    }

    private fun isHdrSnapOutput(name: String): Boolean {
        return name.contains("_HDRSnap_", ignoreCase = true)
    }

    private fun isScreenshotEditor(packageName: String?, className: String?): Boolean {
        val pkg = packageName.orEmpty()
        if (pkg == "com.google.android.apps.pixel.creativeassistant") return true // Pixel Studio
        if (pkg == "com.google.android.markup" || pkg == "com.android.markup") return true

        // Photos edits launched later are only held when Android exposes an editor-ish
        // window class; ordinary browsing in Photos must not stall screenshot processing.
        if (pkg == "com.google.android.apps.photos") {
            val cls = className.orEmpty().lowercase()
            return cls.contains("edit") || cls.contains("editor")
        }
        return false
    }

    private fun isTransientWindow(packageName: String?): Boolean {
        return packageName in setOf(
            "com.android.systemui",
            "com.google.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin"
        )
    }

    private data class PendingScreenshot(
        val id: Long,
        val uri: Uri,
        val name: String,
        val firstSeenMs: Long,
        var lastObservedChangeMs: Long,
        var dateModifiedSeconds: Long,
        var sizeBytes: Long,
        var attempts: Int = 0,
        var superseded: Boolean = false,
        val supersededIds: LinkedHashSet<Long> = linkedSetOf(),
        val supersededUris: LinkedHashSet<Uri> = linkedSetOf()
    )

    private data class MediaRow(
        val id: Long,
        val uri: Uri,
        val name: String,
        val path: String,
        val dateAddedMs: Long,
        val dateModifiedSeconds: Long,
        val sizeBytes: Long,
        val ownerPackage: String?
    )

    companion object {
        // Long enough to tap the Pixel screenshot preview's Edit action. If an editor is
        // detected, this timer is suspended for the entire editing session.
        private const val INITIAL_EDIT_GRACE_MS = 7_000L
        private const val QUIET_AFTER_CHANGE_MS = 1_500L
        private const val POST_EDITOR_GRACE_MS = 2_500L
        private const val MAX_RETRIES = 3
    }
}
