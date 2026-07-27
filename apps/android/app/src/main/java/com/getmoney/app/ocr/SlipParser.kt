package com.getmoney.app.ocr

import java.math.BigDecimal
import java.util.Locale

data class SlipDraft(
    val amount: String,
    val spentAtIso: String? = null,
    val bank: String? = null,
    val reference: String? = null,
    val note: String? = null,
)

object SlipParser {
    private val amountPattern = Regex("""\d{1,3}(?:,\d{3})*(?:\.\d{2})?""")
    private val referencePattern = Regex(
        """(?:รหัสอ้างอิง|Reference|Ref\.?)\s*[:：]?\s*([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val isoDateTimePattern = Regex(
        """(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{2}:\d{2})""",
    )
    private val thaiDateTimePattern = Regex(
        """(\d{1,2})[/-](\d{1,2})[/-](\d{4})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?""",
    )

    private val bankKeywords = listOf(
        "PromptPay" to "PromptPay",
        "พร้อมเพย์" to "PromptPay",
        "Krungthai" to "Krungthai",
        "กรุงไทย" to "Krungthai",
        "KBank" to "KBank",
        "กสิกร" to "KBank",
        "SCB" to "SCB",
        "ไทยพาณิชย์" to "SCB",
        "BBL" to "BBL",
        "กรุงเทพ" to "BBL",
        "KTB" to "KTB",
        "TMB" to "TMB",
        "TTB" to "TTB",
    )

    fun parse(raw: String): SlipDraft {
        val text = raw.trim()
        val amount = extractAmount(text)
        val reference = extractReference(text)
        val bank = extractBank(text)
        val spentAtIso = extractSpentAt(text)

        return SlipDraft(
            amount = amount,
            spentAtIso = spentAtIso,
            bank = bank,
            reference = reference,
        )
    }

    private fun extractAmount(text: String): String {
        val matches = amountPattern.findAll(text).map { it.value }.toList()
        if (matches.isEmpty()) return "0"

        val largest = matches.maxByOrNull { match ->
            match.replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO
        } ?: matches.first()

        return normalizeAmount(largest)
    }

    private fun normalizeAmount(raw: String): String {
        val cleaned = raw.replace(",", "")
        val value = cleaned.toBigDecimalOrNull() ?: return cleaned
        return if (cleaned.contains(".")) {
            String.format(Locale.US, "%.2f", value)
        } else {
            value.stripTrailingZeros().toPlainString()
        }
    }

    private fun extractReference(text: String): String? {
        referencePattern.find(text)?.groupValues?.getOrNull(1)?.let { return it.trim() }

        val fallback = Regex("""\b([A-Z0-9]{8,})\b""").find(text)?.groupValues?.get(1)
        return fallback?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractBank(text: String): String? {
        for ((keyword, bank) in bankKeywords) {
            if (text.contains(keyword, ignoreCase = true)) {
                return bank
            }
        }
        return null
    }

    private fun extractSpentAt(text: String): String? {
        isoDateTimePattern.find(text)?.groupValues?.get(1)?.let { return it }

        val match = thaiDateTimePattern.find(text) ?: return null
        val day = match.groupValues[1].padStart(2, '0')
        val month = match.groupValues[2].padStart(2, '0')
        val year = match.groupValues[3]
        val hour = match.groupValues[4].padStart(2, '0')
        val minute = match.groupValues[5]
        val second = match.groupValues.getOrNull(6)?.ifBlank { "00" } ?: "00"
        return "$year-$month-${day}T$hour:$minute:$second+07:00"
    }
}
