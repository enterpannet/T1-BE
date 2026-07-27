package com.getmoney.app.autoscan

import com.getmoney.app.ocr.SlipDraft
import com.getmoney.app.ocr.SlipIntake
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlipCandidateFilterTest {
    @Test
    fun acceptsQrWithAmount() {
        val outcome = SlipIntake.Outcome(
            draft = SlipDraft(amount = "100.00", reference = "ABC"),
            source = SlipIntake.Source.Qr,
            qrPayload = "000201...",
        )
        assertTrue(SlipCandidateFilter.isCandidate(outcome))
    }

    @Test
    fun rejectsQrWithoutUsableAmount() {
        val outcome = SlipIntake.Outcome(
            draft = SlipDraft(amount = "0.00"),
            source = SlipIntake.Source.Qr,
        )
        assertFalse(SlipCandidateFilter.isCandidate(outcome))
    }

    @Test
    fun acceptsOcrWithAmountAndSlipKeywords() {
        val outcome = SlipIntake.Outcome(
            draft = SlipDraft(amount = "250.50", bank = "SCB"),
            source = SlipIntake.Source.Ocr,
            rawText = "โอนเงิน สำเร็จ จำนวนเงิน 250.50 บาท SCB",
        )
        assertTrue(SlipCandidateFilter.isCandidate(outcome))
    }

    @Test
    fun rejectsOcrRandomPhotoWithNumber() {
        val outcome = SlipIntake.Outcome(
            draft = SlipDraft(amount = "42.00"),
            source = SlipIntake.Source.Ocr,
            rawText = "Happy birthday 42 candles",
        )
        assertFalse(SlipCandidateFilter.isCandidate(outcome))
    }
}
