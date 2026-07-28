package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlipIntakeEnrichTest {
    @Test
    fun fillsBankFromOcrWhenQrMissingBank() {
        val qr = SlipDraft(amount = "100.00", reference = "REF1")
        val ocr = SlipDraft(amount = "999.00", bank = "SCB", spentAtIso = "2026-07-28T10:00:00+07:00")
        val merged = enrichSlipDraftFromOcr(qr, ocr)
        assertEquals("100.00", merged.amount)
        assertEquals("SCB", merged.bank)
        assertEquals("REF1", merged.reference)
        assertEquals("2026-07-28T10:00:00+07:00", merged.spentAtIso)
    }

    @Test
    fun keepsQrBankOverOcrBank() {
        val qr = SlipDraft(amount = "100.00", bank = "KBank")
        val ocr = SlipDraft(amount = "100.00", bank = "SCB")
        val merged = enrichSlipDraftFromOcr(qr, ocr)
        assertEquals("KBank", merged.bank)
    }

    @Test
    fun nullOcrLeavesQrUnchanged() {
        val qr = SlipDraft(amount = "10.00")
        val merged = enrichSlipDraftFromOcr(qr, null)
        assertEquals("10.00", merged.amount)
        assertNull(merged.bank)
    }

    @Test
    fun prefersOcrRecipientOverWeakQrMerchant() {
        val qr = SlipDraft(
            amount = "500.00",
            toName = "PROMPT PAY",
            direction = TransferDirection.OUT,
        )
        val ocr = SlipDraft(
            amount = "500.00",
            fromName = "นาย เกียรติศักดิ์ พ · ธ.กสิกรไทย · xxx-x-x3523-x",
            toName = "น.ส. น้ำทิพย์ ตาทอง · ธ.กสิกรไทย · xxx-x-x5079-x",
            direction = TransferDirection.OUT,
        )
        val merged = enrichSlipDraftFromOcr(qr, ocr)
        assertEquals(ocr.toName, merged.toName)
        assertEquals(ocr.fromName, merged.fromName)
    }
}
