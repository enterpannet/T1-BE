package com.getmoney.app.ocr

import com.getmoney.app.data.identity.MyIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KPlusLayoutFromOcrBoxesTest {
    @Test
    fun oishiWithNoiseEight() {
        val lines = listOf(
            OcrLine("จ่ายบิลสำเร็จ", 49f, 53f),
            OcrLine("8", 32.5f, 230f),
            OcrLine("K+", 83f, 669f),
            OcrLine("4 ก.ค.69 19:24 น.", 108f, 53f),
            OcrLine("นาย เกียรติศักดิ์ พ", 225.5f, 201f),
            OcrLine("ธ.กสิกรไทย", 279.5f, 207f),
            OcrLine("Xxx-x-x3523-x", 336.5f, 206f),
            OcrLine("บจก.โออิชิ ราเมน", 496.5f, 202f),
            OcrLine("แ", 546.5f, 67f),
            OcrLine("92206052", 552f, 206f),
            OcrLine("เลขที่รายการ:", 686.5f, 31f),
            OcrLine("016185192458BPM19816", 735f, 135f),
            OcrLine("จำนวน:", 787.5f, 30f),
            OcrLine("109.00 บาท", 839f, 300f),
            OcrLine("ค่าธรรมเนียม:", 894f, 34f),
            OcrLine("0.00 บาท", 945f, 337f),
            OcrLine("สแกนตรวจสอบสลิป", 951.5f, 546f),
        )
        val d = SlipParser.parse(lines.joinToString("\n") { it.text }, lines)
        println("oishi layout spent=${d.spentAtIso} to=${d.toName} from=${d.fromName} amt=${d.amount}")
        assertEquals("2026-07-04T19:24:00+07:00", d.spentAtIso)
        assertTrue(d.toName!!.contains("โออิชิ"))
        assertTrue(d.fromName!!.contains("เกียรติศักดิ์"))
        assertTrue(!d.toName!!.contains("เกียรติศักดิ์"))
    }

    @Test
    fun promptPaySplitLogo() {
        val lines = listOf(
            OcrLine("โอนเงินสำเร็จ", 42.5f, 46f),
            OcrLine("K+", 80f, 645f),
            OcrLine("4 ก.ค. 69 02:48 น.", 104.5f, 50f),
            OcrLine("นาย เกียรติศักดิ์ พ", 218.5f, 193f),
            OcrLine("ธ.กสิกรไทย", 271.5f, 197f),
            OcrLine("XXx-x-x3523-x", 325.5f, 200f),
            OcrLine("น.ส. ณิชชา องค์สอาด", 483f, 198f),
            OcrLine("Prompt", 505.5f, 44f),
            OcrLine("Pay", 528.5f, 42f),
            OcrLine("รหัสพร้อมเพย์", 531f, 198f),
            OcrLine("xxx-xxx-8242", 585.5f, 199f),
            OcrLine("เลขที่รายการ:", 697.5f, 32f),
            OcrLine("016185024857BPP05348", 745f, 112f),
            OcrLine("จำนวน:", 795f, 29f),
            OcrLine("177.00 บาท", 844.5f, 300f),
            OcrLine("ค่าธรรมเนียม:", 898f, 33f),
            OcrLine("0.00 บาท", 946.5f, 324f),
            OcrLine("สแกนตรวจสอบสลิป", 953.5f, 528f),
        )
        val d = SlipParser.parse(lines.joinToString("\n") { it.text }, lines)
        println("pp layout spent=${d.spentAtIso} to=${d.toName} from=${d.fromName}")
        assertEquals("2026-07-04T02:48:00+07:00", d.spentAtIso)
        assertTrue(d.toName!!.contains("ณิชชา"))
        assertTrue(!d.toName!!.contains("เกียรติศักดิ์"))
    }

    @Test
    fun identityKeepsOtherKiattisakAsRecipient() {
        val raw = """
            โอนเงินสำเร็จ
            K+
            28 ก.ค. 69 02:32 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            นายเกียรติศักดิ์ พิมพ์อาภรณ์
            ธ.กรุงไทย
            xxx-x-x9444-x
            จำนวน: 10.00 บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        val identity = MyIdentity(
            nameAliases = listOf("นาย เกียรติศักดิ์ พิมพ์อาภรณ์"),
            accountHints = listOf("3523"),
        )
        val applied = MyIdentityDirection.applyToDraft(draft, identity)
        println("dir=${applied.direction} from=${applied.fromName} to=${applied.toName} fromMine=${MyIdentityDirection.matchesMe(draft.fromName, identity)} toMine=${MyIdentityDirection.matchesMe(draft.toName, identity)}")
        // Sender account is mine → โอนออก; recipient shares first name but is not me
        assertEquals(TransferDirection.OUT, applied.direction)
        assertTrue(applied.toName!!.contains("พิมพ์อาภรณ์"))
    }
}
