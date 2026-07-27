package com.getmoney.app.ocr

import android.net.Uri

/**
 * Production slip intake: QR (EMV/PromptPay) first, then text OCR fallback.
 * Images never leave the device.
 */
class SlipIntake(
    private val qrScanner: SlipQrScanner,
    private val ocr: SlipOcr,
) {
    data class Outcome(
        val draft: SlipDraft,
        val source: Source,
        val qrPayload: String? = null,
    )

    enum class Source {
        Qr,
        Ocr,
    }

    suspend fun process(uri: Uri): Outcome {
        val payloads = runCatching { qrScanner.scanPayloads(uri) }.getOrDefault(emptyList())
        for (payload in payloads) {
            val parsed = EmvQrParser.parse(payload) ?: continue
            val draft = EmvQrParser.toSlipDraft(parsed) ?: continue
            return Outcome(draft = draft, source = Source.Qr, qrPayload = payload)
        }

        // QR found but no amount — still try OCR; attach QR reference if useful
        val qrRef = payloads.firstNotNullOfOrNull { payload ->
            EmvQrParser.parse(payload)?.reference
        }

        val rawText = ocr.recognize(uri)
        val draft = SlipParser.parse(rawText).let { parsed ->
            if (parsed.reference.isNullOrBlank() && !qrRef.isNullOrBlank()) {
                parsed.copy(reference = qrRef)
            } else {
                parsed
            }
        }
        return Outcome(draft = draft, source = Source.Ocr)
    }
}
