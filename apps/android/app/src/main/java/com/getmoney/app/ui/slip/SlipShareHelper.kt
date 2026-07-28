package com.getmoney.app.ui.slip

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Copy a slip image into cache and build an ACTION_SEND intent with
 * image + debug caption (filename / parsed fields / OCR text).
 */
object SlipShareHelper {
    private const val MAX_OCR_CHARS = 3500

    fun buildShareIntent(
        context: Context,
        sourceUri: Uri,
        fileName: String,
        caption: String,
    ): Intent? {
        val shareUri = prepareShareUri(context, sourceUri, fileName) ?: return null
        val mime = context.contentResolver.getType(sourceUri)?.takeIf { it.startsWith("image/") }
            ?: "image/jpeg"
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, fileName, shareUri)
        }
    }

    fun buildDebugCaption(
        fileName: String,
        amount: String,
        bank: String,
        reference: String,
        fromName: String,
        toName: String,
        rawOcrText: String?,
    ): String = buildString {
        appendLine("GetMoney slip debug")
        appendLine("file: $fileName")
        if (amount.isNotBlank()) appendLine("amount: $amount")
        if (bank.isNotBlank()) appendLine("bank: $bank")
        if (reference.isNotBlank()) appendLine("ref: $reference")
        if (fromName.isNotBlank()) appendLine("from: $fromName")
        if (toName.isNotBlank()) appendLine("to: $toName")
        val ocr = rawOcrText?.trim().orEmpty()
        if (ocr.isNotEmpty()) {
            appendLine("--- OCR ---")
            append(
                if (ocr.length <= MAX_OCR_CHARS) ocr
                else ocr.take(MAX_OCR_CHARS) + "\n…(truncated)",
            )
        }
    }.trim()

    internal fun prepareShareUri(context: Context, sourceUri: Uri, fileName: String): Uri? {
        val safeName = sanitizeFileName(fileName)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        // Keep one share file named clearly for chat apps
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val out = File(dir, safeName)
        return runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                out,
            )
        }.getOrNull()
    }

    internal fun sanitizeFileName(raw: String): String {
        var name = raw.trim().ifEmpty { "slip.jpg" }
        name = name.replace(Regex("""[\\/:*?"<>|]"""), "_")
        if (!name.contains('.')) name = "$name.jpg"
        return name.take(120)
    }
}
