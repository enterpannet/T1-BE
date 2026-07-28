package com.getmoney.app.ocr

import android.net.Uri
import com.getmoney.app.data.identity.MyIdentityStore

/**
 * Production slip intake: QR (EMV/PromptPay) first, then text OCR fallback.
 * When QR wins on amount, still OCR to fill bank / datetime / reference gaps.
 * Images never leave the device during intake.
 */
class SlipIntake(
    private val qrScanner: SlipQrScanner,
    private val ocr: SlipOcr,
    private val myIdentityStore: MyIdentityStore? = null,
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
        val identity = runCatching { myIdentityStore?.getIdentity() }.getOrNull()

        val payloads = runCatching { qrScanner.scanPayloads(uri) }.getOrDefault(emptyList())
        for (payload in payloads) {
            val parsed = EmvQrParser.parse(payload) ?: continue
            val qrDraft = EmvQrParser.toSlipDraft(parsed) ?: continue
            val doc = runCatching { ocr.recognizeDocument(uri) }.getOrNull()
            val ocrDraft = doc?.let { SlipParser.parse(it) }
            val merged = enrichSlipDraftFromOcr(qrDraft, ocrDraft)
            return Outcome(
                draft = MyIdentityDirection.applyToDraft(merged, identity),
                source = Source.Qr,
                qrPayload = payload,
                rawText = doc?.text,
            )
        }

        val qrRef = payloads.firstNotNullOfOrNull { payload ->
            EmvQrParser.parse(payload)?.reference
        }

        val doc = ocr.recognizeDocument(uri)
        val draft = SlipParser.parse(doc).let { parsed ->
            val withRef = if (parsed.reference.isNullOrBlank() && !qrRef.isNullOrBlank()) {
                parsed.copy(reference = qrRef)
            } else {
                parsed
            }
            MyIdentityDirection.applyToDraft(withRef, identity)
        }
        return Outcome(draft = draft, source = Source.Ocr, rawText = doc.text)
    }
}

/** Fill blank QR fields from OCR parse (bank / datetime / reference / parties / note). */
internal fun enrichSlipDraftFromOcr(qrDraft: SlipDraft, ocrDraft: SlipDraft?): SlipDraft {
    if (ocrDraft == null) return qrDraft
    val mergedDirection = qrDraft.direction ?: ocrDraft.direction
    // Slip-verify QR tag 59 is often the payer/bank label, not คนรับ — trust OCR parties
    // whenever OCR found a sender block (typical dual-party slip layout).
    val (mergedFrom, mergedTo) = if (!ocrDraft.fromName.isNullOrBlank()) {
        ocrDraft.fromName to (ocrDraft.toName ?: qrDraft.toName)
    } else {
        preferPartyName(qrDraft.fromName, ocrDraft.fromName) to
            preferPartyName(qrDraft.toName, ocrDraft.toName)
    }
    val qrNote = qrDraft.note?.takeIf { it.isNotBlank() }
    val ocrNote = ocrDraft.note?.takeIf { it.isNotBlank() }
    val qrTransferOnly = qrNote != null &&
        qrNote == SlipNoteCodec.displayTransferLine(
            SlipNoteParts(
                direction = qrDraft.direction,
                fromName = qrDraft.fromName,
                toName = qrDraft.toName,
            ),
        )
    val partiesChanged = mergedFrom != qrDraft.fromName || mergedTo != qrDraft.toName
    val memoKeep = SlipNoteCodec.parse(ocrNote).memo ?: SlipNoteCodec.parse(qrNote).memo
    val rebuiltNote = SlipNoteCodec.compose(mergedDirection, mergedFrom, mergedTo, memoKeep)
    val mergedNote = when {
        partiesChanged && rebuiltNote != null -> rebuiltNote
        ocrNote != null && (qrNote == null || qrTransferOnly) -> ocrNote
        else -> qrNote ?: ocrNote
    }
    return qrDraft.copy(
        bank = qrDraft.bank?.takeIf { it.isNotBlank() } ?: ocrDraft.bank,
        spentAtIso = qrDraft.spentAtIso?.takeIf { it.isNotBlank() } ?: ocrDraft.spentAtIso,
        reference = qrDraft.reference?.takeIf { it.isNotBlank() } ?: ocrDraft.reference,
        note = mergedNote,
        direction = mergedDirection,
        fromName = mergedFrom,
        toName = mergedTo,
    )
}

/** Prefer the party string that looks more like a real slip name (Thai title / Thai letters / account). */
internal fun preferPartyName(qr: String?, ocr: String?): String? {
    val q = qr?.trim()?.takeIf { it.isNotEmpty() }
    val o = ocr?.trim()?.takeIf { it.isNotEmpty() }
    if (o == null) return q
    if (q == null) return o
    return if (partyNameQuality(o) > partyNameQuality(q)) o else q
}

internal fun partyNameQuality(value: String): Int {
    var score = 0
    if (Regex("""นาย|น\.ส\.|นางสาว|นาง|บริษัท|บจก""").containsMatchIn(value)) score += 60
    score += value.count { it in '\u0E00'..'\u0E7F' }
    if (Regex("""(?i)x{2,}[-x0-9]+""").containsMatchIn(value)) score += 25
    if (Regex("""ธ\.|กสิกร|กรุงไทย|PromptPay|พร้อมเพย์""").containsMatchIn(value)) score += 15
    // Short Latin-only QR merchant names score low
    val hasThai = value.any { it in '\u0E00'..'\u0E7F' }
    if (!hasThai && value.length <= 24) score -= 20
    return score
}
