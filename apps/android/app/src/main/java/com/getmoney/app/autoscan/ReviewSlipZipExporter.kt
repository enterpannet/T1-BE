package com.getmoney.app.autoscan

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pack review/error slip images into a ZIP and open the system share sheet.
 */
object ReviewSlipZipExporter {
    private val bangkok = ZoneId.of("Asia/Bangkok")
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun buildShareIntent(context: Context, slips: List<QueuedSlip>): Intent? {
        if (slips.isEmpty()) return null
        val zip = createZip(context, slips) ?: return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zip,
        )
        val name = zip.name
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            putExtra(Intent.EXTRA_TEXT, "GetMoney สลิปที่ต้องตรวจ (${slips.size} ใบ)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, name, uri)
        }
    }

    internal fun createZip(context: Context, slips: List<QueuedSlip>): File? {
        if (slips.isEmpty()) return null
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val name = "getmoney-review-slips-${fileStamp.format(OffsetDateTime.now(bangkok))}.zip"
        val outFile = File(dir, name)
        var written = 0
        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            slips.forEachIndexed { index, slip ->
                val bytes = readUriBytes(context, slip.uri) ?: return@forEachIndexed
                val ext = guessExtension(context, slip.uri, bytes)
                val entryName = "%03d_%s.%s".format(
                    index + 1,
                    safeNamePart(slip.draft.reference ?: slip.draft.amount),
                    ext,
                )
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
                written++
            }
        }
        if (written == 0) {
            outFile.delete()
            return null
        }
        return outFile
    }

    private fun readUriBytes(context: Context, uri: Uri): ByteArray? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedInputStream(input).readBytes()
            }
        }.getOrNull()

    private fun guessExtension(context: Context, uri: Uri, bytes: ByteArray): String {
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        when {
            mime.contains("png") -> return "png"
            mime.contains("webp") -> return "webp"
            mime.contains("jpeg") || mime.contains("jpg") -> return "jpg"
        }
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpg"
        if (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return "png"
        val path = uri.lastPathSegment.orEmpty().lowercase()
        return when {
            path.endsWith(".png") -> "png"
            path.endsWith(".webp") -> "webp"
            else -> "jpg"
        }
    }

    private fun safeNamePart(raw: String): String {
        val cleaned = raw.replace(Regex("""[^A-Za-z0-9\-_.]"""), "_").take(40)
        return cleaned.ifBlank { "slip" }
    }
}
