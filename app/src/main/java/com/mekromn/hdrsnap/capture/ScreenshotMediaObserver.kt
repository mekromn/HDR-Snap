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
import java.util.concurrent.atomic.AtomicBoolean

class ScreenshotMediaObserver(private val context: Context) {
    private val resolver = context.contentResolver
    private val executor = Executors.newSingleThreadExecutor()
    private val processor = ScreenshotProcessor(context)
    private val running = AtomicBoolean(false)
    private val seen = LinkedHashSet<Long>()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            processLatest()
        }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        resolver.registerContentObserver(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            true,
            observer
        )
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        resolver.unregisterContentObserver(observer)
        executor.shutdownNow()
    }

    fun processLatest() {
        if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            HdrSnapPrefs(context).lastStatus = "Photo permission required to inspect system screenshots"
            return
        }

        executor.execute {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_ADDED
            )

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
                var scanned = 0
                while (cursor.moveToNext() && scanned++ < 12) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val path = cursor.getString(pathCol) ?: ""
                    if (!isScreenshot(name, path)) continue

                    val isNew = synchronized(seen) {
                        val added = seen.add(id)
                        if (added) {
                            while (seen.size > 64) seen.remove(seen.first())
                        }
                        added
                    }
                    if (!isNew) continue

                    processor.process(ContentUris.withAppendedId(collection, id), name)
                    break
                }
            }
        }
    }

    private fun isScreenshot(name: String, path: String): Boolean {
        if (name.contains("HDRSnap", ignoreCase = true)) return false
        return name.startsWith("Screenshot", ignoreCase = true) ||
            path.contains("Screenshots", ignoreCase = true)
    }
}
