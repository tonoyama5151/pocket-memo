package com.tonoyama.pocketmemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Background images are copied into app storage at a small size, because the
 * home screen widget receives them as a bitmap and large ones would not fit.
 */
object MemoImages {
    private const val MAX_SIDE = 512

    private fun dir(context: Context) = File(context.filesDir, "bg").apply { mkdirs() }

    fun file(context: Context, name: String) = File(dir(context), name)

    fun delete(context: Context, name: String) {
        if (name.isNotEmpty() && !Backgrounds.isPattern(name)) file(context, name).delete()
    }

    /** Copies the picked image into app storage and returns its file name, or null on failure. */
    fun importImage(context: Context, uri: Uri, memoId: String): String? = try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) {
            null
        } else {
            var sample = 1
            while (longest / (sample * 2) >= MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (decoded == null) {
                null
            } else {
                val rotation = resolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
                val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
                val matrix = Matrix().apply {
                    if (scale < 1f) postScale(scale, scale)
                    if (rotation != 0f) postRotate(rotation)
                }
                val finalBitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                val name = "$memoId-${System.currentTimeMillis()}.jpg"
                FileOutputStream(file(context, name)).use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                if (finalBitmap !== decoded) finalBitmap.recycle()
                decoded.recycle()
                name
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Copies a stored photo for a duplicated memo. Patterns and "" are returned as they are. */
    fun copy(context: Context, name: String, newMemoId: String): String {
        if (name.isEmpty() || Backgrounds.isPattern(name)) return name
        val src = file(context, name)
        if (!src.exists()) return ""
        val target = "$newMemoId-${System.currentTimeMillis()}.jpg"
        return try {
            src.copyTo(file(context, target))
            target
        } catch (e: Exception) {
            ""
        }
    }

    /** The stored photo as saved, before any brightness adjustment. */
    fun loadRaw(context: Context, name: String): Bitmap? {
        if (name.isEmpty() || Backgrounds.isPattern(name)) return null
        val f = file(context, name)
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        return try {
            BitmapFactory.decodeFile(f.absolutePath, opts)
        } catch (e: Exception) {
            null
        }
    }
}
