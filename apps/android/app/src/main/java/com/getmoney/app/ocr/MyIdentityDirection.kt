package com.getmoney.app.ocr

import com.getmoney.app.data.identity.MyIdentity

/**
 * Infer โอนออก/รับเข้า by matching slip parties against the user's saved identity.
 */
object MyIdentityDirection {
    fun infer(
        fromName: String?,
        toName: String?,
        identity: MyIdentity?,
    ): TransferDirection? {
        if (identity == null || !identity.hasAny()) return null
        val fromMine = matchesMe(fromName, identity)
        val toMine = matchesMe(toName, identity)
        return when {
            fromMine && !toMine -> TransferDirection.OUT
            toMine && !fromMine -> TransferDirection.IN
            else -> null
        }
    }

    fun applyToDraft(draft: SlipDraft, identity: MyIdentity?): SlipDraft {
        val inferred = infer(draft.fromName, draft.toName, identity) ?: return draft
        if (draft.direction == inferred) return draft
        val memo = SlipNoteCodec.parse(draft.note).memo
        val note = SlipNoteCodec.compose(inferred, draft.fromName, draft.toName, memo)
            ?: draft.note
        return draft.copy(direction = inferred, note = note)
    }

    internal fun matchesMe(party: String?, identity: MyIdentity): Boolean {
        if (party.isNullOrBlank()) return false
        val partyCore = nameCoreForMatch(party)
        if (partyCore.length < 3) {
            // Fall through to account hints only
        } else {
            for (alias in identity.nameAliases) {
                val aliasCore = significantThaiLatin(normalizeParty(alias))
                if (aliasCore.length < 3) continue
                if (partyCore == aliasCore) return true
                // Full identity contains truncated K+ slip name (same person).
                if (partyCore.length >= 6 && aliasCore.contains(partyCore)) return true
                // Slip contains identity only when lengths are close — avoid matching
                // short "นายเกียรติศักดิ์พ" against someone else's full surname.
                if (partyCore.contains(aliasCore) && partyCore.length <= aliasCore.length + 3) {
                    return true
                }
            }
        }

        for (hint in identity.accountHints) {
            if (accountHintMatches(party, hint)) return true
        }
        return false
    }

    private fun accountHintMatches(party: String, hint: String): Boolean {
        val compactParty = party.replace(" ", "")
        val compactHint = hint.replace(" ", "")
        if (compactHint.length >= 4 && compactParty.contains(compactHint, ignoreCase = true)) {
            return true
        }
        val hintDigits = hint.filter { it.isDigit() }
        if (hintDigits.length >= 4) {
            val last4 = hintDigits.takeLast(4)
            // xxx-x-x3523-x style
            if (Regex("""(?i)x{2,}[-x0-9]*$last4""").containsMatchIn(compactParty)) return true
            if (compactParty.contains(last4)) return true
        }
        return false
    }

    private fun normalizeParty(raw: String): String =
        raw.lowercase()
            .replace(Regex("""\s+"""), "")
            .replace("·", "")
            .replace(".", "")

    /** Name letters only — strip masked accounts so truncation matching stays stable. */
    private fun nameCoreForMatch(party: String): String {
        val withoutAccount = party.replace(Regex("""(?i)[·\s]*x{2,}[-x0-9]*"""), "")
        return significantThaiLatin(normalizeParty(withoutAccount))
    }

    private fun significantThaiLatin(normalized: String): String =
        normalized.filter {
            it in '\u0e00'..'\u0e7f' || it in 'a'..'z' || it in '0'..'9'
        }
}
