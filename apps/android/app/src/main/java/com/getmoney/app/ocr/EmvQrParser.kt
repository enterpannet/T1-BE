package com.getmoney.app.ocr

/**
 * Minimal EMVCo QR / PromptPay TLV parser.
 * Focus: extract amount (tag 54), currency (53), and reference-ish fields from tag 62.
 */
object EmvQrParser {
    data class Result(
        val amount: String? = null,
        val currency: String? = null,
        val reference: String? = null,
        val merchantName: String? = null,
        val rawPayload: String,
    )

    fun parse(payload: String): Result? {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return null

        // Some bank "verify slip" QRs are URLs — try query params.
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return parseUrl(trimmed)
        }

        // EMV payloads usually start with "0002" (tag 00 length 02)
        if (!trimmed.startsWith("00") || trimmed.length < 10) {
            return null
        }

        val root = parseTlvs(trimmed) ?: return null
        val amount = root["54"]?.let { normalizeAmount(it) }
        val currency = root["53"]
        val merchantName = root["59"]
        val additional = root["62"]?.let { parseTlvs(it) }
        val reference = additional?.get("05")
            ?: additional?.get("01")
            ?: additional?.get("07")
            ?: additional?.get("08")

        if (amount == null && reference == null && merchantName == null) {
            return null
        }

        return Result(
            amount = amount,
            currency = currency,
            reference = reference,
            merchantName = merchantName,
            rawPayload = trimmed,
        )
    }

    fun toSlipDraft(result: Result, bankHint: String? = null): SlipDraft? {
        val amount = result.amount ?: return null
        return SlipDraft(
            amount = amount,
            bank = bankHint ?: currencyToBankHint(result.currency),
            reference = result.reference,
            note = result.merchantName?.let { "QR: $it" },
        )
    }

    private fun currencyToBankHint(currency: String?): String? = null

    private fun parseUrl(url: String): Result? {
        val query = url.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return null
        val params = query.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null
            else part.substring(0, idx).lowercase() to
                java.net.URLDecoder.decode(part.substring(idx + 1), Charsets.UTF_8)
        }.toMap()

        val amountRaw = params["amount"]
            ?: params["amt"]
            ?: params["transactionamount"]
            ?: params["tranamount"]
        val amount = amountRaw?.let { normalizeAmount(it) }
        val reference = params["ref"]
            ?: params["reference"]
            ?: params["txnid"]
            ?: params["transactionid"]
            ?: params["transid"]

        if (amount == null && reference == null) return null
        return Result(
            amount = amount,
            reference = reference,
            rawPayload = url,
        )
    }

    private fun normalizeAmount(raw: String): String? {
        val cleaned = raw.trim().replace(",", "")
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value <= java.math.BigDecimal.ZERO) return null
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    }

    /** Parse contiguous EMV TLVs: TTLLVALUE... */
    internal fun parseTlvs(payload: String): Map<String, String>? {
        val out = linkedMapOf<String, String>()
        var i = 0
        while (i + 4 <= payload.length) {
            val tag = payload.substring(i, i + 2)
            val lenStr = payload.substring(i + 2, i + 4)
            val len = lenStr.toIntOrNull() ?: return null
            i += 4
            if (len < 0 || i + len > payload.length) return null
            val value = payload.substring(i, i + len)
            out[tag] = value
            i += len
        }
        // Allow trailing CRC noise only if we parsed at least one tag
        return if (out.isEmpty()) null else out
    }
}
