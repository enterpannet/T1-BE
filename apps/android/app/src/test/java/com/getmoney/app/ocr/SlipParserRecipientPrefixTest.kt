package com.getmoney.app.ocr

import org.junit.Assert.assertTrue
import org.junit.Test

class SlipParserRecipientPrefixTest {
    @Test
    fun titledMissWithGarbageLinesBetweenNameAndBank() {
        val raw = """
            โอนเงินสำเร็จ
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            Xxx-x-x3523-x
            %
            น.ส. นำทิพย์ ตาทอง
            6
            ธ.กสิกรไทย
            Xxx-x-x5079-x
            จำนวน: 500.00 บาท
        """.trimIndent()
        val d = SlipParser.parse(raw)
        assertTrue(d.toName!!.contains("นำทิพย์"))
        assertTrue(d.toName!!.contains("Xxx-x-x5079-x") || d.toName!!.contains("xxx-x-x5079-x"))
    }

    @Test
    fun nangsawPromptPayRecipient() {
        val raw = """
            โอนเงินสำเร็จ
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            Xxx-x-x3523-x
            นางสาว วิชุฎา สารขันธ์
            Prompt
            Pay
            รหัสพร้อมเพย์
            xxx-xxx-3455
            จำนวน: 2,348.00 บาท
        """.trimIndent()
        val d = SlipParser.parse(raw)
        assertTrue(d.toName!!.contains("วิชุฎา"))
        assertTrue(d.toName!!.contains("xxx-xxx-3455"))
    }

    @Test
    fun companySplitAsBorisatPlusMahachonBecomesBomj() {
        val raw = """
            จ่ายบิลสำเร็จ
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            XXX-x-x3523-x
            บริษัท ซันเวนดิ้ง เทคโนโลยีจำกัด
            1
            (มหาชน)
            PO2SP0185659680S
            จำนวน: 17.00 บาท
        """.trimIndent()
        val d = SlipParser.parse(raw)
        assertTrue(d.toName!!.contains("บมจ") || d.toName!!.contains("ซันเวนดิ้ง"))
        assertTrue(d.toName!!.contains("ซันเวนดิ้ง"))
    }

    @Test
    fun explicitBomjPrefix() {
        val raw = """
            จ่ายบิลสำเร็จ
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            บมจ. ซีพี แอ็กซ์ตร้า
            1234567890123
            จำนวน: 100.00 บาท
        """.trimIndent()
        val d = SlipParser.parse(raw)
        assertTrue(d.toName!!.contains("บมจ"))
        assertTrue(d.toName!!.contains("ซีพี") || d.toName!!.contains("แอ็กซ์ตร้า"))
    }

    @Test
    fun fullSurnamePersonRecipient() {
        val raw = """
            โอนเงินสำเร็จ
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            นายเกียรติศักดิ์ พิมพ์อาภรณ์
            ธ.กรุงไทย
            xxx-x-x9444-x
            จำนวน: 10.00 บาท
        """.trimIndent()
        val d = SlipParser.parse(raw)
        assertTrue(d.toName!!.contains("พิมพ์อาภรณ์"))
    }
}
