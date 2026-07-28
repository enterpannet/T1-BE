package com.getmoney.app.ui.tx

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.getmoney.app.data.api.TransactionResponse
import com.getmoney.app.ocr.SlipNoteCodec
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Build Excel-friendly CSV (UTF-8 BOM) and share via FileProvider.
 */
object TransactionCsvExporter {
    private val bangkok = ZoneId.of("Asia/Bangkok")
    private val displayStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    private val headers = listOf(
        "วันที่โอน",
        "จำนวน",
        "ธนาคาร",
        "ทิศทาง",
        "จาก",
        "ถึง",
        "บันทึกช่วยจำ",
        "แหล่งที่มา",
        "บันทึกเมื่อ",
    )

    fun toCsv(transactions: List<TransactionResponse>): String {
        val body = buildString {
            append(headers.joinToString(",") { escapeField(it) })
            append("\r\n")
            // Newest first to match Tx screen
            transactions.sortedByDescending { it.spentAt }.forEach { tx ->
                val parts = SlipNoteCodec.parse(tx.note)
                append(
                    listOf(
                        formatDateTime(tx.spentAt),
                        tx.amount,
                        tx.bank.orEmpty(),
                        parts.direction?.labelTh().orEmpty(),
                        parts.fromName.orEmpty(),
                        parts.toName.orEmpty(),
                        parts.memo.orEmpty(),
                        sourceLabel(tx.source),
                        formatDateTime(tx.createdAt),
                    ).joinToString(",") { escapeField(it) },
                )
                append("\r\n")
            }
        }
        // BOM so Excel on Windows opens Thai correctly
        return "\uFEFF$body"
    }

    fun buildShareIntent(context: Context, transactions: List<TransactionResponse>): Intent? {
        if (transactions.isEmpty()) return null
        val csv = toCsv(transactions)
        val name = "getmoney-transactions-${fileStamp.format(OffsetDateTime.now(bangkok))}.csv"
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val out = File(dir, name)
        out.writeText(csv, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            out,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            putExtra(Intent.EXTRA_TEXT, "GetMoney transactions (${transactions.size})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, name, uri)
        }
    }

    internal fun escapeField(raw: String): String {
        val needsQuotes = raw.contains(',') ||
            raw.contains('"') ||
            raw.contains('\n') ||
            raw.contains('\r')
        if (!needsQuotes) return raw
        return "\"" + raw.replace("\"", "\"\"") + "\""
    }

    private fun formatDateTime(iso: String): String =
        runCatching {
            OffsetDateTime.parse(iso).atZoneSameInstant(bangkok).format(displayStamp)
        }.getOrElse { iso }

    private fun sourceLabel(source: String): String = when (source.lowercase()) {
        "slip" -> "slip"
        "manual" -> "manual"
        else -> source
    }
}
