package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Real slips that still fail date on re-read (K+ bill pay + Krungthai). */
class SlipDateRescanFailTest {
    @Test
    fun kplusBillPayJuly8CleanText() {
        val d = SlipParser.parse(
            """
            จ่ายบิลสำเร็จ
            8 ก.ค. 69 14:30 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            CP AXTRA PUBLIC COMPANY LIMITED (HEAD OFFICE)
            000002206013179
            460923086DNIVC000000
            เลขที่รายการ 016189143011BPM08005
            จำนวน 98.00 บาท
            """.trimIndent(),
        )
        assertEquals("2026-07-08T14:30:00+07:00", d.spentAtIso)
    }

    @Test
    fun kplusBillPayTxnIdWithLetterIAsOne() {
        // OCR often reads 1 as I in เลขที่รายการ
        val d = SlipParser.parse(
            """
            จ่ายบิลสำเร็จ
            8 n.A. 69 14:30 H.
            01618914301IBPM08005
            98.00 บาท
            """.trimIndent(),
        )
        assertEquals("2026-07-08T14:30:11+07:00", d.spentAtIso)
    }

    @Test
    fun kplusBillPayJuly9ApmTxn() {
        val d = SlipParser.parse(
            """
            จ่ายบิลสำเร็จ
            9 .. 69 15:13 H.
            CP AXTRA
            016190151316APM19280
            100.00 บาท
            """.trimIndent(),
        )
        assertEquals("2026-07-09T15:13:16+07:00", d.spentAtIso)
    }

    @Test
    fun krungthaiDateWithDashBeforeTime() {
        val d = SlipParser.parse(
            """
            โอนเงินสำเร็จ
            รหัสอ้างอิง A8de95febabc24bf6
            จาก
            นายเกียรติศักดิ์ พ
            กรุงไทย
            XXX-X-XX444-0
            ไปยัง
            นาย เกียรติศักดิ์ พิมพ์อาภรณ์
            กสิกรไทย
            XXX-X-XX523-2
            จำนวนเงิน 3,389.00 บาท
            ค่าธรรมเนียม 0.00 บาท
            วันที่ทำรายการ 09 ก.ค. 2569 - 05:54
            """.trimIndent(),
        )
        assertNotNull(d.spentAtIso)
        assertEquals("2026-07-09T05:54:00+07:00", d.spentAtIso)
    }

    @Test
    fun kplusBillPayJuly9BpmFromTxnOnly() {
        val d = SlipParser.parse(
            """
            จ่ายบิลสำเร็จ
            MINOR DQ LIMITED
            016190150030BPM07446
            30.00 บาท
            """.trimIndent(),
        )
        assertEquals("2026-07-09T15:00:30+07:00", d.spentAtIso)
    }

    @Test
    fun filenameHintRecoversTxnDate() {
        val doc = OcrDocument(
            text = """
                จ่ายบิลสำเร็จ
                8 n.. 69 14:30 H.
                CP AXTRA
                98.00 บาท
            """.trimIndent(),
            lines = emptyList(),
            engine = OcrDocument.Engine.Paddle,
        )
        val d = SlipParser.parse(
            withFileNameHint(doc, "01618914301IBPM08005-dc6f8649.png"),
        )
        assertEquals("2026-07-08T14:30:11+07:00", d.spentAtIso)
    }
}
