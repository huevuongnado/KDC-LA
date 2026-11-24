package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageStorageHelper {
    private const val MAX_FIRESTORE_BASE64_LENGTH = 200_000

    suspend fun saveCompressedImage(
        context: Context,
        sourceUri: Uri,
        prefix: String,
        maxDimension: Int = 1600,
        quality: Int = 90,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaledBitmap(context, sourceUri, maxDimension) ?: return@runCatching null
            val dir = File(context.filesDir, "portal_images").apply { mkdirs() }
            val outFile = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
            bitmap.recycle()
            outFile.absolutePath
        }.getOrNull()
    }

    /**
     * Compresses an image into a compact JPEG Base64 data URI suitable for syncing inside PostDoc.imageData
     * under the Firestore security rule bound (<= 250,000 bytes).
     */
    suspend fun encodeCompressedBase64(
        context: Context,
        sourceUri: Uri,
        maxDimension: Int = 720,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaledBitmap(context, sourceUri, maxDimension) ?: return@runCatching null
            var quality = 68
            var result: String? = null
            while (quality >= 25) {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                val encoded = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                if (encoded.length <= MAX_FIRESTORE_BASE64_LENGTH) {
                    result = encoded
                    break
                }
                quality -= 12
            }
            bitmap.recycle()
            result
        }.getOrNull()
    }

    fun decodeBase64Bitmap(imageData: String): Bitmap? {
        if (imageData.isBlank()) return null
        return runCatching {
            val cleanBase64 = imageData.substringAfter("base64,", imageData)
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun decodeScaledBitmap(context: Context, sourceUri: Uri, maxDimension: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOpts)
        }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while (max(origW, origH) / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOpts)
        } ?: return null

        val scale = min(1f, maxDimension.toFloat() / max(decoded.width, decoded.height))
        return if (scale < 1f) {
            val targetW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }
    }
}
