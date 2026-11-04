package com.godly.camera.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.godly.camera.storage.MediaStoreSaver
import kotlin.math.max

/** Plug-in point. A future ML model (denoise, deblur...) would implement this interface. No ML model is bundled in v0.1. */
interface ImageEnhancer {
    val name: String
    fun enhance(src: Bitmap): Bitmap
}

/** Real, lightweight, non-AI processing: mild unsharp mask (3x3 box blur). Does not invent detail. */
class UnsharpMaskEnhancer(private val amount: Float = 0.6f) : ImageEnhancer {
    override val name = "Light sharpen (unsharp mask)"

    override fun enhance(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sr = 0; var sg = 0; var sb = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, h - 1)
                    for (dx in -1..1) {
                        val p = px[yy * w + (x + dx).coerceIn(0, w - 1)]
                        sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
                    }
                }
                val o = px[y * w + x]
                val r = (o shr 16) and 0xFF; val g = (o shr 8) and 0xFF; val b = o and 0xFF
                val nr = (r + amount * (r - sr / 9f)).toInt().coerceIn(0, 255)
                val ng = (g + amount * (g - sg / 9f)).toInt().coerceIn(0, 255)
                val nb = (b + amount * (b - sb / 9f)).toInt().coerceIn(0, 255)
                out[y * w + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}

object EnhanceRunner {
    private const val MAX_SIDE = 4000

    /** Runs on a background thread. Returns the Uri of the NEW enhanced copy, or null on failure. */
    fun enhanceCopy(ctx: Context, src: Uri, enhancer: ImageEnhancer = UnsharpMaskEnhancer()): Uri? {
        return try {
            val r = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            r.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = r.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val orientation = r.openInputStream(src)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
            }
            MediaStoreSaver.saveEnhancedJpeg(ctx, enhancer.enhance(bmp))
        } catch (t: Throwable) {
            null
        }
    }
}
