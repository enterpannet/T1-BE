package com.getmoney.app.ocr

import com.getmoney.app.data.identity.MyIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlipQrVsOcrRecipientTest {
    @Test
    fun ocrBillerWinsOverQrThaiPayerMerchant() {
        // Slip-verify QR tag 59 often has the payer / bank label, not the biller.
        val qr = SlipDraft(
            amount = "109.00",
            toName = "นาย เกียรติศักดิ์ พ",
            direction = TransferDirection.OUT,
        )
        val ocr = SlipDraft(
            amount = "109.00",
            spentAtIso = "2026-07-04T19:24:00+07:00",
            fromName = "นาย เกียรติศักดิ์ พ · ธ.กสิกรไทย · Xxx-x-x3523-x",
            toName = "บจก.โออิชิ ราเมน · 92206052",
            direction = TransferDirection.OUT,
            bank = "KBank",
        )
        val merged = enrichSlipDraftFromOcr(qr, ocr)
        assertEquals("2026-07-04T19:24:00+07:00", merged.spentAtIso)
        assertTrue(merged.toName!!.contains("โออิชิ"))
        assertTrue(merged.fromName!!.contains("เกียรติศักดิ์"))
        assertTrue(merged.fromName!!.contains("3523"))
    }

    @Test
    fun ocrLatinBillerWinsOverQrThaiPayer() {
        val qr = SlipDraft(
            amount = "298.53",
            toName = "นาย เกียรติศักดิ์ พ",
            direction = TransferDirection.OUT,
        )
        val ocr = SlipDraft(
            amount = "298.53",
            spentAtIso = "2026-07-04T13:35:00+07:00",
            fromName = "นาย เกียรติศักดิ์ พ · ธ.กสิกรไทย · XXX-x-x3523-x",
            toName = "FINN MOBILE",
            direction = TransferDirection.OUT,
        )
        val merged = enrichSlipDraftFromOcr(qr, ocr)
        assertEquals("FINN MOBILE", merged.toName)
        assertEquals("2026-07-04T13:35:00+07:00", merged.spentAtIso)
    }

    @Test
    fun shortAliasDoesNotMatchOtherPersonFullName() {
        val identity = MyIdentity(
            nameAliases = listOf("นาย เกียรติศักดิ์ พ"),
            accountHints = listOf("3523"),
        )
        assertTrue(
            MyIdentityDirection.matchesMe(
                "นาย เกียรติศักดิ์ พ · xxx-x-x3523-x",
                identity,
            ),
        )
        assertFalse(
            MyIdentityDirection.matchesMe(
                "นายเกียรติศักดิ์ พิมพ์อาภรณ์ · ธ.กรุงไทย · xxx-x-x9444-x",
                identity,
            ),
        )
    }

    @Test
    fun fullAliasStillMatchesTruncatedSlipName() {
        val identity = MyIdentity(
            nameAliases = listOf("นาย เกียรติศักดิ์ พิมพ์อาภรณ์"),
            accountHints = emptyList(),
        )
        assertTrue(
            MyIdentityDirection.matchesMe(
                "นาย เกียรติศักดิ์ พ · xxx-x-x3523-x",
                identity,
            ),
        )
    }
}
