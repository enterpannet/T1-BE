package com.getmoney.app.ocr

import java.math.BigDecimal
import java.math.RoundingMode

data class SlipDraft(
    val amount: String,
    val spentAtIso: String? = null,
    val bank: String? = null,
    val reference: String? = null,
    val note: String? = null,
)

object SlipParser {
    private val thaiDigits = mapOf(
        '๐' to '0', '๑' to '1', '๒' to '2', '๓' to '3', '๔' to '4',
        '๕' to '5', '๖' to '6', '๗' to '7', '๘' to '8', '๙' to '9',
    )

    /** Money-like: 1,250.50 / 1250.50 / 1250 */
    private val moneyPattern = Regex(
        """(?<![A-Za-z0-9])(\d{1,3}(?:,\d{3})+|\d{1,7})(?:\.(\d{1,2}))?(?![A-Za-z0-9])""",
    )

    private val amountKeyword = Regex(
        """จำนวน\s*เงิน|ยอด(?:โอน|เงิน|ชำระ)?|Amount|Transfer\s*Amount|Total\s*Amount|Paid\s*Amount|จำนวน|บาท|Baht|THB|฿""",
        RegexOption.IGNORE_CASE,
    )

    private val referencePattern = Regex(
        """(?:รหัสอ้างอิง|เลขที่อ้างอิง|Reference(?:\s*No\.?)?|Ref(?:erence)?\.?|Txn(?:\s*ID)?|Transaction\s*ID)\s*[:：#]?\s*([A-Za-z0-9\-]+)""",
        RegexOption.IGNORE_CASE,
    )

    private val isoDateTimePattern = Regex(
        """(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:[+-]\d{2}:\d{2}|Z)?)""",
    )

    private val thaiDateTimePattern = Regex(
        """(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\s+(\d{1,2})[:.](\d{2})(?::(\d{2}))?""",
    )

    private val bankKeywords = listOf(
        "PromptPay" to "PromptPay",
        "พร้อมเพย์" to "PromptPay",
        "Krungthai" to "Krungthai",
        "กรุงไทย" to "Krungthai",
        "KBank" to "KBank",
        "Kasikorn" to "KBank",
        "กสิกร" to "KBank",
        "SCB" to "SCB",
        "ไทยพาณิชย์" to "SCB",
        "BBL" to "BBL",
        "Bangkok Bank" to "BBL",
        "กรุงเทพ" to "BBL",
        "KTB" to "KTB",
        "TMB" to "TMB",
        "TTB" to "TTB",
        "ธนชาต" to "TTB",
        "Krungsri" to "Krungsri",
        "กรุงศรี" to "Krungsri",
        "TrueMoney" to "TrueMoney",
        "ShopeePay" to "ShopeePay",
        "Rabbit LINE Pay" to "Rabbit LINE Pay",
    )

    fun parse(raw: String): SlipDraft {
        val text = normalizeOcrText(raw)
        return SlipDraft(
            amount = extractAmount(text),
            spentAtIso = extractSpentAt(text),
            bank = extractBank(text),
            reference = extractReference(text),
        )
    }

    internal fun normalizeOcrText(raw: String): String {
        val mapped = buildString(raw.length) {
            for (ch in raw) {
                append(thaiDigits[ch] ?: ch)
            }
        }
        return mapped
            .replace('\u00A0', ' ')
            .replace(Regex("""[|!]"""), "I") // OCR noise near amounts rarely
            .replace(Regex("""[Oo](?=\d)|(?<=\d)[Oo]"""), "0")
            .replace(Regex("""(?<=\d)[lI](?=\d)"""), "1")
            .replace(Regex("""[‐‑–—]"""), "-")
            .replace(Regex("""[：﹕]"""), ":")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    private fun extractAmount(text: String): String {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return "0"

        data class Candidate(val value: BigDecimal, val score: Int)

        val candidates = mutableListOf<Candidate>()

        lines.forEachIndexed { index, line ->
            val nearby = listOfNotNull(
                lines.getOrNull(index - 1),
                line,
                lines.getOrNull(index + 1),
            ).joinToString(" ")

            val keywordNear = amountKeyword.containsMatchIn(nearby)
            val keywordOnLine = amountKeyword.containsMatchIn(line)

            moneyPattern.findAll(line).forEach { match ->
                val rawNumber = match.value
                val value = parseMoney(rawNumber) ?: return@forEach
                if (!isPlausibleMoney(value, rawNumber)) return@forEach

                var score = 0
                if (keywordOnLine) score += 120
                else if (keywordNear) score += 80

                if (rawNumber.contains('.')) {
                    score += 50
                    val decimals = rawNumber.substringAfter('.', "")
                    if (decimals.length == 2) score += 30
                }
                if (rawNumber.contains(',')) score += 20

                // Prefer typical transfer sizes over tiny fees or huge IDs
                when {
                    value >= BigDecimal("10") && value <= BigDecimal("100000") -> score += 25
                    value < BigDecimal("1") -> score -= 40
                    value > BigDecimal("500000") -> score -= 60
                }

                // Same-line currency token
                if (Regex("""บาท|Baht|THB|฿""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                    score += 40
                }

                candidates += Candidate(value, score)
            }
        }

        val best = candidates.maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { it.value })
            ?: return "0"

        return best.value.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

    private fun parseMoney(raw: String): BigDecimal? =
        raw.replace(",", "").toBigDecimalOrNull()

    private fun isPlausibleMoney(value: BigDecimal, raw: String): Boolean {
        val digitsOnly = raw.replace(",", "").replace(".", "")
        // Account / phone / reference-like long integers without decimals
        if (!raw.contains('.') && digitsOnly.length >= 10) return false
        // Years
        if (!raw.contains('.') && value in BigDecimal("1900")..BigDecimal("2100") && digitsOnly.length == 4) {
            return false
        }
        // Phone starting with 0 and 9–10 digits
        if (raw.startsWith("0") && !raw.contains('.') && digitsOnly.length in 9..10) return false
        if (value <= BigDecimal.ZERO) return false
        if (value > BigDecimal("2000000")) return false
        return true
    }

    private fun extractReference(text: String): String? {
        referencePattern.find(text)?.groupValues?.getOrNull(1)?.let { return it.trim() }

        // Avoid treating money as reference: require mix of letters+digits or long alnum
        val fallback = Regex("""\b([A-Z]{2,}\d{6,}|\d{6,}[A-Z]{2,}[A-Z0-9]*)\b""")
            .find(text)
            ?.groupValues
            ?.get(1)
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
        isoDateTimePattern.find(text)?.groupValues?.get(1)?.let { iso ->
            return if (iso.endsWith("Z") || Regex("""[+-]\d{2}:\d{2}$""").containsMatchIn(iso)) {
                iso
            } else {
                "${iso}+07:00"
            }
        }

        val match = thaiDateTimePattern.find(text) ?: return null
        val day = match.groupValues[1].padStart(2, '0')
        val month = match.groupValues[2].padStart(2, '0')
        var year = match.groupValues[3]
        if (year.length == 2) year = "20$year"
        // Buddhist year → Gregorian
        val yearInt = year.toIntOrNull()
        if (yearInt != null && yearInt > 2400) {
            year = (yearInt - 543).toString()
        }
        val hour = match.groupValues[4].padStart(2, '0')
        val minute = match.groupValues[5]
        val second = match.groupValues.getOrNull(6)?.ifBlank { null } ?: "00"
        return "$year-$month-${day}T$hour:$minute:${second.padStart(2, '0')}+07:00"
    }
}
