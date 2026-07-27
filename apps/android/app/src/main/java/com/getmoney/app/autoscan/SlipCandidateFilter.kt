package com.getmoney.app.autoscan

import com.getmoney.app.ocr.SlipIntake

object SlipCandidateFilter {
    private val slipKeyword = Regex(
        """จำนวน\s*เงิน|ยอด(?:โอน|เงิน|ชำระ)?|โอน(?:เงิน|สำเร็จ)?|PromptPay|พร้อมเพย์|Amount|Transfer|Baht|THB|฿|ธนาคาร|Bank""",
        RegexOption.IGNORE_CASE,
    )

    fun isCandidate(outcome: SlipIntake.Outcome): Boolean {
        if (!hasPositiveAmount(outcome.draft.amount)) return false
        return when (outcome.source) {
            SlipIntake.Source.Qr -> true
            SlipIntake.Source.Ocr -> {
                !outcome.draft.bank.isNullOrBlank() ||
                    !outcome.draft.reference.isNullOrBlank() ||
                    slipKeyword.containsMatchIn(outcome.rawText.orEmpty())
            }
        }
    }

    private fun hasPositiveAmount(raw: String): Boolean {
        val value = raw.trim().replace(",", "").toDoubleOrNull() ?: return false
        return value > 0.0
    }
}
