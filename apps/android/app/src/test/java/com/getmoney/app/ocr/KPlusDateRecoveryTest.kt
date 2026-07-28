package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KPlusDateRecoveryTest {
    @Test
    fun txnIdEncodesJuly4BillPay() {
        val d = SlipParser.parse(
            """
            จ่ายบิลสำเร็จ
            K+
            4 n.. 69 19:24 H.
            XXX-X-X3523-X
            92206052
            O16185192458BPM19816
            109.00
            """.trimIndent(),
        )
        assertEquals("2026-07-04T19:24:58+07:00", d.spentAtIso)
        assertEquals("016185192458BPM19816", d.reference)
    }

    @Test
    fun txnIdEncodesJuly26Diy() {
        val d = SlipParser.parse(
            """
            ชำระเงินสำเร็จ
            K+
            26 n.A. 69 18:21 H.
            XXX-X-x3523-X
            202607262676195
            016207182108BQR03012
            191.00
            """.trimIndent(),
        )
        assertEquals("2026-07-26T18:21:08+07:00", d.spentAtIso)
    }

    @Test
    fun garbledMonthNaStillParsesWithoutTxn() {
        val d = SlipParser.parse(
            """
            โอนเงินสำเร็จ
            K+
            27 n.A. 69 18:34 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            จำนวน: 70.00 บาท
            """.trimIndent(),
        )
        assertEquals("2026-07-27T18:34:00+07:00", d.spentAtIso)
    }

    @Test
    fun promptPaySplitLinesBecomePayee() {
        val d = SlipParser.parse(
            """
            โอนเงินสำเร็จ
            K+
            27 ก.ค. 69 18:34 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            Prompt
            Pay
            xxx-xxx-7817
            เลขที่รายการ: 016208183401BPP08608
            จำนวน: 70.00 บาท
            """.trimIndent(),
        )
        assertNotNull(d.toName)
        assertTrue(d.toName!!.contains("PromptPay"))
        assertTrue(d.toName!!.contains("xxx-xxx-7817") || d.toName!!.contains("7817"))
        assertEquals("2026-07-27T18:34:00+07:00", d.spentAtIso)
    }

    @Test
    fun storylogStillRecipient() {
        val d = SlipParser.parse(
            """
            จ่ายบิลสำเร็จ
            K+
            1 .. 69 01:05 u.
            XXX-X-X3523-X
            STORYLOG
            800000260601007414
            000QH4X62RXXG2E05Q
            O16152010501BPM17567
            200.00
            """.trimIndent(),
        )
        assertEquals("2026-06-01T01:05:01+07:00", d.spentAtIso)
        assertTrue(d.toName!!.contains("STORYLOG"))
    }
}
