package com.getmoney.app.data.slipimage

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class SlipImageStore private constructor(
    private val rootDir: File,
    private val contentResolver: ContentResolver?,
    private val maxSidePx: Int = DEFAULT_MAX_SIDE_PX,
    private val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
) {
    constructor(rootDir: File) : this(rootDir, null)

    constructor(context: Context) : this(context.filesDir, context.contentResolver)

    private val slipsDir: File
        get() = rootDir.resolve("slips")

    suspend fun saveFromUri(transactionId: String, source: Uri): File? = withContext(Dispatchers.IO) {
        slipsDir.mkdirs()
        val destination = slipsDir.resolve("$transactionId.jpg")
        val temp = File.createTempFile("$transactionId-", ".jpg.tmp", slipsDir)
        try {
            val input = openInputStream(source) ?: run {
                temp.delete()
                return@withContext null
            }
            input.use { stream ->
                if (!compressToJpeg(stream, temp, maxSidePx, jpegQuality)) {
                    temp.delete()
                    return@withContext null
                }
            }
            destination.delete()
            if (!temp.renameTo(destination)) {
                temp.delete()
                return@withContext null
            }
            destination
        } catch (e: CancellationException) {
            temp.delete()
            throw e
        } catch (_: Exception) {
            temp.delete()
            null
        }
    }

    fun fileFor(transactionId: String): File? {
        val file = slipsDir.resolve("$transactionId.jpg")
        return file.takeIf { it.exists() }
    }

    fun delete(transactionId: String) {
        fileFor(transactionId)?.delete()
    }

    private fun openInputStream(source: Uri): InputStream? {
        if (source.scheme == "file") {
            val path = source.path ?: return null
            val file = File(path)
            return if (file.exists()) file.inputStream() else null
        }
        return contentResolver?.openInputStream(source)
    }

    companion object {
        const val DEFAULT_MAX_SIDE_PX = 1280
        const val DEFAULT_JPEG_QUALITY = 75

        internal fun compressToJpeg(
            input: InputStream,
            output: File,
            maxSidePx: Int = DEFAULT_MAX_SIDE_PX,
            jpegQuality: Int = DEFAULT_JPEG_QUALITY,
        ): Boolean {
            val original = BitmapFactory.decodeStream(input) ?: return false
            try {
                val scaled = scaleDown(original, maxSidePx)
                try {
                    output.outputStream().use { out ->
                        if (!scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)) {
                            return false
                        }
                    }
                    return output.exists() && output.length() > 0L
                } finally {
                    if (scaled !== original) scaled.recycle()
                }
            } finally {
                original.recycle()
            }
        }

        internal fun scaleDown(source: Bitmap, maxSidePx: Int): Bitmap {
            val w = source.width
            val h = source.height
            val longest = maxOf(w, h)
            if (longest <= maxSidePx || maxSidePx <= 0) return source
            val scale = maxSidePx.toFloat() / longest.toFloat()
            val nw = (w * scale).toInt().coerceAtLeast(1)
            val nh = (h * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(source, nw, nh, true)
        }
    }
}
