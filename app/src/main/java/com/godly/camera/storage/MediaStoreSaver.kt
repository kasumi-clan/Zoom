package com.godly.camera.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.video.MediaStoreOutputOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** All saving goes through MediaStore (no storage permission needed on Android 10+). Names are unique per millisecond and MediaStore never overwrites. */
object MediaStoreSaver {
    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    fun photoOptions(ctx: Context): ImageCapture.OutputFileOptions {
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "GODLY_${stamp()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GodlyCamera")
        }
        return ImageCapture.OutputFileOptions.Builder(
            ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v
        ).build()
    }

    fun videoOptions(ctx: Context): MediaStoreOutputOptions {
        val v = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "GODLY_${stamp()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GodlyCamera")
        }
        return MediaStoreOutputOptions.Builder(ctx.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(v).build()
    }

    /** Saves an enhanced COPY. The original photo is never touched. */
    fun saveEnhancedJpeg(ctx: Context, bmp: Bitmap): Uri? {
        val r = ctx.contentResolver
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "GODLY_${stamp()}_enhanced.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GodlyCamera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return null
        return try {
            r.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } ?: error("no stream")
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            r.update(uri, done, null, null)
            uri
        } catch (e: Exception) {
            r.delete(uri, null, null)
            null
        }
    }
}
