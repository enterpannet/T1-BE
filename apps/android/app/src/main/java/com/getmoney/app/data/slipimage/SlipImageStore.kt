package com.getmoney.app.data.slipimage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class SlipImageStore private constructor(
    private val rootDir: File,
    private val contentResolver: ContentResolver?,
) {
    constructor(rootDir: File) : this(rootDir, null)

    constructor(context: Context) : this(context.filesDir, context.contentResolver)

    private val slipsDir: File
        get() = rootDir.resolve("slips")

    suspend fun saveFromUri(transactionId: String, source: Uri): File? = withContext(Dispatchers.IO) {
        try {
            slipsDir.mkdirs()
            val destination = slipsDir.resolve("$transactionId.jpg")
            openInputStream(source)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            destination
        } catch (_: Exception) {
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
}
