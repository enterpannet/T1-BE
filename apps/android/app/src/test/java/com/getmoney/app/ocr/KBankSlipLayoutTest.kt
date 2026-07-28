package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KBankSlipLayoutTest {
    @Test
    fun yOrderBeatsShuffledTextOrder() {
        // Deliberately wrong join order; Y centers match real K+ stack.
        val lines = listOf(
            OcrLine("จำนวน: 500.00 บาท", yCenter = 900f),
            OcrLine("น.ส. น้ำทิพย์ ตาทอง", yCenter = 500f),
            OcrLine("โอนเงินสำเร็จ", yCenter = 10f),
            OcrLine("นาย เกียรติศักดิ์ พ", yCenter = 200f),
            OcrLine("ธ.กสิกรไทย", yCenter = 250f),
            OcrLine("xxx-x-x3523-x", yCenter = 300f),
            OcrLine("ธ.กสิกรไทย", yCenter = 550f),
            OcrLine("xxx-x-x5079-x", yCenter = 600f),
            OcrLine("7 มิ.ย. 69 07:03 น.", yCenter = 50f),
            OcrLine("K+", yCenter = 30f),
            OcrLine("เลขที่รายการ: 016158070348ATF02045", yCenter = 850f),
            OcrLine("ค่าธรรมเนียม: 0.00 บาท", yCenter = 950f),
        )
        val shuffledText = lines.joinToString("\n") { it.text }
        val draft = SlipParser.parse(shuffledText, lines)
        assertEquals("500.00", draft.amount)
        assertEquals("KBank", draft.bank)
        assertTrue(draft.fromName!!.contains("เกียรติศักดิ์"))
        assertTrue(draft.fromName!!.contains("xxx-x-x3523-x"))
        assertTrue(draft.toName!!.contains("น้ำทิพย์"))
        assertTrue(draft.toName!!.contains("xxx-x-x5079-x"))
    }

    @Test
    fun kPlusLogoSplitStillDetected() {
        val raw = """
            โอนเงินสำเร็จ
            K
            +
            28 ก.ค. 69 02:32 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            นายเกียรติศักดิ์ พิมพ์อาภรณ์
            ธ.กรุงไทย
            xxx-x-x9444-x
            เลขที่รายการ: 016209023213BOR02751
            จำนวน: 10.00 บาท
        """.trimIndent()
        assertTrue(SlipParser.isLikelyKbankSlip(raw))
        val draft = SlipParser.parse(raw)
        assertEquals("10.00", draft.amount)
        assertTrue(draft.fromName!!.contains("เกียรติศักดิ์ พ"))
        assertTrue(draft.toName!!.contains("พิมพ์อาภรณ์"))
    }

    @Test
    fun billerAfterFirstMaskUsesSectionSplit() {
        val lines = listOf(
            OcrLine("จ่ายบิลสำเร็จ", 10f),
            OcrLine("K+", 20f),
            OcrLine("1 มิ.ย. 69 01:05 น.", 40f),
            OcrLine("นาย เกียรติศักดิ์ พ", 100f),
            OcrLine("ธ.กสิกรไทย", 130f),
            OcrLine("Xxx-x-x3523-x", 160f),
            OcrLine("STORYLOG", 280f),
            OcrLine("800000260601007414", 310f),
            OcrLine("000QH4X62RXXG2E05Q", 340f),
            OcrLine("เลขที่รายการ: 016152010501BPM17567", 400f),
            OcrLine("จำนวน: 200.00 บาท", 430f),
        )
        val draft = SlipParser.parse(lines.joinToString("\n") { it.text }, lines)
        assertEquals("200.00", draft.amount)
        assertTrue(draft.fromName!!.contains("เกียรติศักดิ์"))
        assertTrue(draft.toName!!.contains("STORYLOG"))
        assertTrue(draft.toName!!.contains("800000260601007414"))
    }

    @Test
    fun largestGapSplitsWhenNoMaskOnFrom() {
        // Rare bill path: from name+bank, to company, no xxx on from
        val lines = listOf(
            OcrLine("จ่ายบิลสำเร็จ", 10f),
            OcrLine("K+", 20f),
            OcrLine("27 ก.ค. 69 22:17 น.", 40f),
            OcrLine("นาย เกียรติศักดิ์ พ", 100f),
            OcrLine("ธ.กสิกรไทย", 130f),
            // big gap = arrow
            OcrLine("บริษัท บลูเพย์ จำกัด(บลูเพย์-ลาซาด้าเพย์)", 280f),
            OcrLine("เลขที่รายการ: 016208221700BPM19678", 400f),
            OcrLine("จำนวน: 1,180.00 บาท", 430f),
            OcrLine("ค่าธรรมเนียม: 0.00 บาท", 460f),
        )
        val draft = SlipParser.parse(lines.joinToString("\n") { it.text }, lines)
        assertEquals("1180.00", draft.amount)
        assertTrue(draft.fromName!!.contains("เกียรติศักดิ์"))
        assertTrue(draft.toName!!.contains("บลูเพย์") || draft.toName!!.contains("บริษัท"))
    }
}
