package com.getmoney.app.ocr

import android.net.Uri

/**
 * Production slip intake: QR (EMV/PromptPay) first, then text OCR fallback.
 * When QR wins on amount, still OCR to fill bank / datetime / reference gaps.
 * Images never leave the device during intake.
 */
class SlipIntake(
    private val qrScanner: SlipQrScanner,
    private val ocr: SlipOcr,
) {
    data class Outcome(
        val draft: SlipDraft,
        val source: Source,
        val qrPayload: String? = null,
        val rawText: String? = null,
    )

    enum class Source {
        Qr,
        Ocr,
    }

    suspend fun process(uri: Uri): Outcome {
        val payloads = runCatching { qrScanner.scanPayloads(uri) }.getOrDefault(emptyList())
        for (payload in payloads) {
            val parsed = EmvQrParser.parse(payload) ?: continue
            val qrDraft = EmvQrParser.toSlipDraft(parsed) ?: continue
            val rawText = runCatching { ocr.recognize(uri) }.getOrNull()
            val ocrDraft = rawText?.let { SlipParser.parse(it) }
            val merged = enrichSlipDraftFromOcr(qrDraft, ocrDraft)
            return Outcome(
                draft = merged,
                source = Source.Qr,
                qrPayload = payload,
                rawText = rawText,
            )
        }

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
        return Outcome(draft = draft, source = Source.Ocr, rawText = rawText)
    }
}

/** Fill blank QR fields from OCR parse (bank / datetime / reference / note). */
internal fun enrichSlipDraftFromOcr(qrDraft: SlipDraft, ocrDraft: SlipDraft?): SlipDraft {
    if (ocrDraft == null) return qrDraft
    return qrDraft.copy(
        bank = qrDraft.bank?.takeIf { it.isNotBlank() } ?: ocrDraft.bank,
        spentAtIso = qrDraft.spentAtIso?.takeIf { it.isNotBlank() } ?: ocrDraft.spentAtIso,
        reference = qrDraft.reference?.takeIf { it.isNotBlank() } ?: ocrDraft.reference,
        note = qrDraft.note?.takeIf { it.isNotBlank() } ?: ocrDraft.note,
    )
}
