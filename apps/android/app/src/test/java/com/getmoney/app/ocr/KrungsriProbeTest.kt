package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KrungsriProbeTest {
    @Test
    fun trueMoneyCleanKeepsPayeeSeparate() {
        val d = SlipParser.parse(
            """
            เติมเงินสำเร็จ
            krungsri
            08 มิ.ย. 2569 22:02:26
            KIATTISAK PIM
            XXX-1-73041-X
            ทรูมันนี่ (e-Wallet)
            XXX-0-02065-X
            จำนวนเงิน
            300.00
            THB
            หมายเลขอ้างอิง KSA00000000605270053
            """.trimIndent(),
        )
        assertEquals("2026-06-08T22:02:26+07:00", d.spentAtIso)
        assertTrue(d.fromName!!.contains("KIATTISAK"))
        assertFalse(d.fromName!!.contains("ทรู"))
        assertTrue(d.toName!!.contains("ทรู") || d.toName!!.contains("True", ignoreCase = true))
        assertTrue(d.toName!!.contains("XXX-0-02065-X"))
    }

    @Test
    fun garbledLatinMonthStillParses() {
        val d = SlipParser.parse(
            """
            ชำระเงินสำเร็จ
            krungsri
            18 n.A. 2569 14:06:58
            KIATTISAK PIM
            XXX-1-73041-X
            LINE
            Pay
            XXX-0-00096-X
            จำนวนเงิน 400.00 THB
            20260718160642407530
            20260718
            Rabbit Line Pay
            KS0000000724889683
            """.trimIndent(),
        )
        assertEquals("2026-07-18T14:06:58+07:00", d.spentAtIso)
        assertNotNull(d.toName)
        assertTrue(
            d.toName!!.contains("Rabbit", ignoreCase = true) ||
                d.toName!!.contains("LINE", ignoreCase = true),
        )
        assertTrue(d.toName!!.contains("Rabbit", ignoreCase = true))
    }

    @Test
    fun garbledJuneMonthUj() {
        val d = SlipParser.parse(
            """
            เติมเงินสำเร็จ
            krungsri
            08 U.J. 2569 22:02:26
            KIATTISAK PIM
            XXX-1-73041-X
            nsjuu (e-Wallet)
            truemoney
            wallet
            XXX-0-02065-X
            จำนวนเงิน 300.00 THB
            KSA00000000605270053
            """.trimIndent(),
        )
        assertEquals("2026-06-08T22:02:26+07:00", d.spentAtIso)
        assertTrue(d.toName!!.contains("ทรู") || d.toName!!.contains("e-Wallet", ignoreCase = true))
        assertFalse(d.fromName!!.contains("truemoney", ignoreCase = true))
    }

    @Test
    fun embeddedYmdWhenMonthUnknown() {
        val d = SlipParser.parse(
            """
            Scan to Pay สำเร็จ
            krungsri
            16 ?? 2569 15:23:05
            KIATTISAK PIM
            XXX-1-73041-X
            จำนวนเงิน 60.00 THB
            20260616152305001234
            KSA00000000626978183
            """.trimIndent(),
        )
        assertEquals("2026-06-16T15:23:05+07:00", d.spentAtIso)
        assertEquals(TransferDirection.OUT, d.direction)
    }

    @Test
    fun thungNgernMaskedAccountAttached() {
        val d = SlipParser.parse(
            """
            Scan to Pay สำเร็จ
            krungsri
            16 มิ.ย. 2569 15:23:05
            KIATTISAK PIM
            XXX-1-73041-X
            ถุงเงิน (หมี่คลุกฮารมณ์ดี)
            010-753-7-XXXX820-5
            จำนวนเงิน 60.00 THB
            KSA00000000626978183
            """.trimIndent(),
        )
        assertEquals("2026-06-16T15:23:05+07:00", d.spentAtIso)
        assertTrue(d.toName!!.contains("ถุงเงิน"))
        assertTrue(d.toName!!.contains("XXXX820") || d.toName!!.contains("010-753"))
    }
}
