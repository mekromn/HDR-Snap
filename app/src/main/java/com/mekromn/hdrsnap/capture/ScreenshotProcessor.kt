package com.mekromn.hdrsnap.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Gainmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.mekromn.hdrsnap.HdrSnapPrefs
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class ScreenshotProcessor(private val context: Context) {
    private val resolver = context.contentResolver
    private val prefs = HdrSnapPrefs(context)

    fun process(
        sourceUri: Uri,
        sourceName: String,
        supersededSources: Collection<Uri> = emptyList()
    ): ProcessResult {
        return try {
            val bitmap = decode(sourceUri)
            val nativeHdr = bitmap.hasGainmap()

            val output = when {
                nativeHdr && prefs.convertTrueHdrToJpegR ->
                    saveUltraHdr(bitmap, sourceName, Provenance.SYSTEM_HDR_GAINMAP)

                nativeHdr -> {
                    val status = "True HDR detected; conversion disabled, source retained"
                    prefs.lastStatus = status
                    return ProcessResult(true, false, status)
                }

                prefs.sdrUpconversionEnabled -> {
                    attachSyntheticGainmap(bitmap)
                    saveUltraHdr(bitmap, sourceName, Provenance.SDR_UPCONVERTED)
                }

                else -> {
                    val status = "SDR screenshot detected; SDR upconversion disabled, source retained"
                    prefs.lastStatus = status
                    return ProcessResult(true, false, status)
                }
            }

            val deleteSummary = deleteSourcesAfterVerifiedOutput(
                listOf(sourceUri) + supersededSources,
                output.uri
            )

            val prefix = if (nativeHdr) {
                "True HDR preserved"
            } else {
                "SDR upconverted with provenance"
            }

            val status = when (deleteSummary) {
                DeleteSummary.NOT_REQUESTED -> "$prefix → ${output.name}; original retained by setting"
                DeleteSummary.DELETED -> "$prefix → ${output.name}; original removed"
                DeleteSummary.NEEDS_ACCESS -> "$prefix → ${output.name}; original retained — grant Media management access for automatic replacement"
                DeleteSummary.PARTIAL -> "$prefix → ${output.name}; replacement verified, but one or more source files could not be removed"
            }
            prefs.lastStatus = status
            ProcessResult(true, true, status)
        } catch (t: Throwable) {
            val status = "Processing failed; original kept: ${t.javaClass.simpleName}: ${t.message ?: "unknown error"}"
            prefs.lastStatus = status
            ProcessResult(false, false, status)
        }
    }

    fun canDeleteWithoutPrompt(): Boolean {
        return Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(context)
    }

    private fun decode(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun saveUltraHdr(bitmap: Bitmap, sourceName: String, provenance: Provenance): SavedOutput {
        check(bitmap.hasGainmap()) { "Refusing to create Ultra HDR output without a gainmap" }

        val stem = sourceName.substringBeforeLast('.')
        val suffix = when (provenance) {
            Provenance.SYSTEM_HDR_GAINMAP -> "_HDRSnap_UltraHDR"
            Provenance.SDR_UPCONVERTED -> "_HDRSnap_SDR-UPCONVERTED_UltraHDR"
        }
        val displayName = "$stem$suffix.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Screenshots"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val outputUri = resolver.insert(collection, values)
            ?: error("MediaStore insert failed")

        try {
            resolver.openOutputStream(outputUri, "w").use { stream ->
                requireNotNull(stream) { "Unable to open output stream" }
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                    "Android gainmap JPEG encoder failed"
                }
            }

            writeProvenanceExif(outputUri, provenance)

            // This is the transaction barrier. No source file may be deleted until the
            // final image reopens successfully and still exposes an embedded gainmap.
            val verified = decode(outputUri)
            check(verified.hasGainmap()) {
                "Post-encode verification failed: JPEG no longer contains a gainmap"
            }

            resolver.update(
                outputUri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
            return SavedOutput(outputUri, displayName)
        } catch (t: Throwable) {
            resolver.delete(outputUri, null, null)
            throw t
        }
    }

    private fun deleteSourcesAfterVerifiedOutput(
        sources: Collection<Uri>,
        verifiedOutput: Uri
    ): DeleteSummary {
        if (!prefs.deleteOriginalAfterVerify) return DeleteSummary.NOT_REQUESTED
        if (!canDeleteWithoutPrompt()) return DeleteSummary.NEEDS_ACCESS

        var failed = false
        sources.distinct().forEach { uri ->
            if (uri == verifiedOutput) return@forEach
            try {
                resolver.delete(uri, null, null)
            } catch (_: SecurityException) {
                failed = true
            } catch (_: Throwable) {
                failed = true
            }
        }
        return if (failed) DeleteSummary.PARTIAL else DeleteSummary.DELETED
    }

    private fun writeProvenanceExif(uri: Uri, provenance: Provenance) {
        resolver.openFileDescriptor(uri, "rw").use { pfd ->
            requireNotNull(pfd) { "Unable to open JPEG for EXIF metadata" }
            val exif = ExifInterface(pfd.fileDescriptor)
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, provenance.description)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, provenance.userComment)
            exif.setAttribute(
                ExifInterface.TAG_SOFTWARE,
                "HDR Snap ${com.mekromn.hdrsnap.BuildConfig.VERSION_NAME}"
            )
            exif.saveAttributes()
        }
    }

    private fun attachSyntheticGainmap(base: Bitmap) {
        check(!base.hasGainmap()) { "Synthetic HDR is only allowed for SDR inputs" }

        val width = base.width
        val height = base.height
        val pixels = IntArray(width * height)
        base.getPixels(pixels, 0, width, 0, 0, width, height)
        val map = ByteArray(pixels.size)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val x = ((luma - 0.45f) / 0.55f).coerceIn(0f, 1f)
            val smooth = x * x * (3f - 2f * x)
            map[i] = (smooth * 255f).roundToInt().coerceIn(0, 255).toByte()
        }

        val gainBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        gainBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(map))

        val gainmap = Gainmap(gainBitmap).apply {
            setRatioMin(1f, 1f, 1f)
            setRatioMax(4f, 4f, 4f)
            setGamma(1f, 1f, 1f)
            setMinDisplayRatioForHdrTransition(1f)
            setDisplayRatioForFullHdr(4f)
            if (Build.VERSION.SDK_INT >= 36) {
                setGainmapDirection(Gainmap.GAINMAP_DIRECTION_SDR_TO_HDR)
            }
        }
        base.setGainmap(gainmap)
    }

    data class SavedOutput(val uri: Uri, val name: String)
    data class ProcessResult(val success: Boolean, val producedReplacement: Boolean, val status: String)

    private enum class DeleteSummary {
        NOT_REQUESTED,
        DELETED,
        NEEDS_ACCESS,
        PARTIAL
    }

    enum class Provenance(val description: String, val userComment: String) {
        SYSTEM_HDR_GAINMAP(
            description = "HDR Snap: native Android HDR screenshot gainmap preserved",
            userComment = "HDRSnapSource=SYSTEM_HDR_GAINMAP; HDRSnapNativeHDR=true; HDRSnapNotice=Gainmap preserved from Android system HDR screenshot"
        ),
        SDR_UPCONVERTED(
            description = "HDR Snap: SDR screenshot upconverted to HDR; NOT native HDR",
            userComment = "HDRSnapSource=SDR_UPCONVERTED; HDRSnapNativeHDR=false; HDRSnapNotice=HDR gainmap synthesized from an SDR screenshot; not a native HDR capture"
        )
    }
}
