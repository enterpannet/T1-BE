package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DateFailProbeTest {
    @Test
    fun oishiNoSpaceBetweenMonthAndYear() {
        val d = SlipParser.parse(
            """
            จ่ายบิลสำเร็จ
            K+
            4 ก.ค.69 19:24 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            Xxx-x-x3523-x
            บจก.โออิชิ ราเมน
            จำนวน: 109.00 บาท
            """.trimIndent(),
        )
        assertEquals("2026-07-04T19:24:00+07:00", d.spentAtIso)
    }

    @Test
    fun diySpacedDate() {
        val d = SlipParser.parse(
            """
            ชำระเงินสำเร็จ
            K+
            26 ก.ค. 69 18:21 น.
            จำนวน: 191.00 บาท
            """.trimIndent(),
        )
        assertEquals("2026-07-26T18:21:00+07:00", d.spentAtIso)
    }

    @Test
    fun daySplitOntoOwnLine() {
        val d = SlipParser.parse(
            """
            โอนเงินสำเร็จ
            27
            ก.ค. 69 18:34 น.
            จำนวน: 70.00 บาท
            """.trimIndent(),
        )
        assertEquals("2026-07-27T18:34:00+07:00", d.spentAtIso)
    }

    @Test
    fun layoutYOrderRecoversDateWhenTextJoinWrong() {
        val doc = OcrDocument(
            text = """
                จำนวน: 150.00 บาท
                โอนเงินสำเร็จ
                นาย เกียรติศักดิ์ พ
            """.trimIndent(),
            lines = listOf(
                OcrLine("โอนเงินสำเร็จ", yCenter = 10f),
                OcrLine("26 ก.ค. 69 17:57 น.", yCenter = 25f),
                OcrLine("นาย เกียรติศักดิ์ พ", yCenter = 80f),
                OcrLine("จำนวน: 150.00 บาท", yCenter = 200f),
            ),
            engine = OcrDocument.Engine.Paddle,
        )
        val d = SlipParser.parse(doc)
        assertEquals("2026-07-26T17:57:00+07:00", d.spentAtIso)
        assertEquals("150.00", d.amount)
    }

    @Test
    fun enrichKeepsOcrDate() {
        val qr = SlipDraft(amount = "109.00", toName = "นาย เกียรติศักดิ์ พ")
        val ocr = SlipDraft(
            amount = "109.00",
            spentAtIso = "2026-07-04T19:24:00+07:00",
            fromName = "นาย เกียรติศักดิ์ พ · xxx",
            toName = "บจก.โออิชิ",
        )
        val m = enrichSlipDraftFromOcr(qr, ocr)
        assertEquals("2026-07-04T19:24:00+07:00", m.spentAtIso)
        assertNotNull(m.toName)
    }
}
