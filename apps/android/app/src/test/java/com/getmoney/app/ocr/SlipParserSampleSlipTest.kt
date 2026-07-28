package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures approximate OCR line order from real Thai bank e-slips
 * (KBank K+, MAKE, Krungthai, Krungsri) — no image bytes in repo.
 */
class SlipParserSampleSlipTest {
    @Test
    fun kbankTransferWithMemo() {
        val raw = """
            โอนเงินสำเร็จ
            7 มิ.ย. 69 07:03 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            น.ส. น้ำทิพย์ ตาทอง
            ธ.กสิกรไทย
            xxx-x-x5079-x
            เลขที่รายการ: 016158070348ATF02045
            จำนวน: 500.00 บาท
            ค่าธรรมเนียม: 0.00 บาท
            บันทึกช่วยจำ: ค่าแรงคนงาน 7/6/2026
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("500.00", draft.amount)
        assertEquals(TransferDirection.OUT, draft.direction)
        assertEquals("KBank", draft.bank)
        assertEquals("2026-06-07T07:03:00+07:00", draft.spentAtIso)
        assertEquals("016158070348ATF02045", draft.reference)
        assertTrue(draft.fromName!!.contains("นาย เกียรติศักดิ์ พ"))
        assertTrue(draft.fromName!!.contains("xxx-x-x3523-x"))
        assertTrue(draft.toName!!.contains("น.ส. น้ำทิพย์ ตาทอง"))
        assertTrue(draft.toName!!.contains("xxx-x-x5079-x"))
        assertEquals("ค่าแรงคนงาน 7/6/2026", draft.note)
    }

    @Test
    fun kbankBillPaymentPrefersAmountOverFee() {
        val raw = """
            จ่ายบิลสำเร็จ
            27 ก.ค. 69 22:17 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            บริษัท บลูเพย์ จำกัด(บลูเพย์-ลาซาด้าเพย์)
            เลขที่รายการ: 016208221700BPM19678
            จำนวน: 1,180.00 บาท
            ค่าธรรมเนียม: 0.00 บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("1180.00", draft.amount)
        assertEquals(TransferDirection.OUT, draft.direction)
        assertTrue(draft.toName!!.contains("บลูเพย์") || draft.toName!!.contains("บริษัท"))
        assertEquals("2026-07-27T22:17:00+07:00", draft.spentAtIso)
    }

    @Test
    fun krungthaiBillWithExplicitFromToLabels() {
        val raw = """
            จ่ายบิลสำเร็จ
            รหัสอ้างอิง C20260727620822224628
            จาก
            นายเกียรติศักดิ์ พ ***
            กรุงไทย
            XXX-X-XX444-0
            ไปยัง
            BLUEPAY CO.,LTD.
            (Bluepay-LazadaPay)
            ค่าอ้างอิง 1 C5S1760034752983040
            จำนวนเงิน 4,985.00 บาท
            ค่าธรรมเนียม 0.00 บาท
            วันที่ทำรายการ 27 ก.ค. 2569 - 22:14
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("4985.00", draft.amount)
        assertEquals("KTB", draft.bank)
        assertEquals(TransferDirection.OUT, draft.direction)
        assertTrue(draft.fromName!!.contains("เกียรติศักดิ์"))
        assertTrue(draft.toName!!.contains("BLUEPAY") || draft.toName!!.contains("บลู"))
        assertEquals("2026-07-27T22:14:00+07:00", draft.spentAtIso)
        assertEquals("C20260727620822224628", draft.reference)
    }

    @Test
    fun krungthaiPrefersPrimaryReferenceOverBillerRef() {
        val raw = """
            จ่ายบิลสำเร็จ
            ค่าอ้างอิง 1 C5S1760034752983040
            จาก
            นายทดสอบ
            กรุงไทย
            ไปยัง
            BLUEPAY
            รหัสอ้างอิง C20260727620822224628
            จำนวนเงิน 100.00 บาท
        """.trimIndent()
        assertEquals("C20260727620822224628", SlipParser.parse(raw).reference)
    }

    @Test
    fun krungthaiTopUpTrueMoney() {
        val raw = """
            เติมเงินสำเร็จ
            รหัสอ้างอิง mKTB4956147634
            จาก
            นายเกียรติศักดิ์ พ ***
            กรุงไทย
            ไปยัง
            ทรู มันนี่ วอลเล็ท
            (1022)
            จำนวนเงิน 1,000.00 บาท
            ค่าธรรมเนียม 0.00 บาท
            วันที่ทำรายการ 06 ก.ค. 2569 - 09:56
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("1000.00", draft.amount)
        assertEquals(TransferDirection.OUT, draft.direction)
        assertTrue(draft.toName!!.contains("ทรู") || draft.toName!!.contains("มันนี่"))
        assertEquals("2026-07-06T09:56:00+07:00", draft.spentAtIso)
        assertEquals("mKTB4956147634", draft.reference)
    }

    @Test
    fun krungsriLinePay() {
        val raw = """
            ชำระเงินสำเร็จ
            18 ก.ค. 2569 14:06:58
            KIATTISAK PIM
            XXX-1-73041-X
            ไลน์ เพย์
            XXX-0-00096-X
            จำนวนเงิน 400.00 THB
            ค่าธรรมเนียม 0.00 THB
            หมายเลขที่อ้างอิง KS0000000724889683
            Krungsri
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("400.00", draft.amount)
        assertEquals("Krungsri", draft.bank)
        assertEquals(TransferDirection.OUT, draft.direction)
        assertTrue(draft.fromName!!.contains("KIATTISAK PIM"))
        assertTrue(draft.fromName!!.contains("XXX-1-73041-X"))
        assertTrue(draft.toName!!.contains("ไลน์") || draft.toName!!.contains("LINE"))
        assertEquals("2026-07-18T14:06:58+07:00", draft.spentAtIso)
        assertEquals("KS0000000724889683", draft.reference)
    }

    @Test
    fun krungsriUsesReferenceLabelWithoutTee() {
        val raw = """
            ชำระเงินสำเร็จ
            18 ก.ค. 2569 14:06:58
            KIATTISAK PIM
            XXX-1-73041-X
            จำนวนเงิน 400.00 THB
            หมายเลขอ้างอิง KS0000000724889683
            กรุงศรี
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("Krungsri", draft.bank)
        assertEquals("KS0000000724889683", draft.reference)
    }

    /** OCR often splits Krungsri header date across two lines. */
    @Test
    fun krungsriSplitDateAndTimeLines() {
        val raw = """
            เติมเงินสำเร็จ
            krungsri
            กรุงศรี
            08 มิ.ย.
            2569 22:02:26
            KIATTISAK PIM
            XXX-1-73041-X
            ทรูมันนี่
            จำนวนเงิน
            300.00
            THB
            หมายเลขอ้างอิง KSA00000000605270053
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("300.00", draft.amount)
        assertEquals("2026-06-08T22:02:26+07:00", draft.spentAtIso)
    }

    /** OCR sometimes inserts a stray digit before the Buddhist year. */
    @Test
    fun krungsriOcrJunkDigitBeforeYear() {
        val raw = """
            Scan to Pay สำเร็จ
            กรุงศรี
            16 มิ.ย. 2
            2569 15:23:56
            จำนวนเงิน 10.00 THB
            หมายเลขอ้างอิง KSA00000000626978183
        """.trimIndent()
        assertEquals("2026-06-16T15:23:56+07:00", SlipParser.parse(raw).spentAtIso)
    }

    @Test
    fun krungsriOcrJunkDigitGluedToMonth() {
        val raw = """
            เติมเงินสำเร็จ
            กรุงศรี
            19 มิ.ย.2
            2569 09:19:58
            จำนวนเงิน 200.00 THB
        """.trimIndent()
        assertEquals("2026-06-19T09:19:58+07:00", SlipParser.parse(raw).spentAtIso)
    }

    @Test
    fun krungsriMonthAbbreviationWithoutTrailingDot() {
        val raw = """
            โอนเงินสำเร็จ
            กรุงศรี
            07 มิ.ย 2569 16:47:34
            จำนวนเงิน 1,500.00 THB
        """.trimIndent()
        assertEquals("2026-06-07T16:47:34+07:00", SlipParser.parse(raw).spentAtIso)
    }

    @Test
    fun recipientWithoutTitleStillExtracted() {
        val raw = """
            โอนเงินสำเร็จ
            7 มิ.ย. 69 07:03 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            น้ำทิพย์ ตาทอง
            ธ.กสิกรไทย
            xxx-x-x5079-x
            จำนวน: 500.00 บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertTrue(draft.fromName!!.contains("เกียรติศักดิ์"))
        assertTrue(draft.toName!!.contains("น้ำทิพย์"))
        assertTrue(draft.toName!!.contains("xxx-x-x5079-x"))
    }

    @Test
    fun recipientTitleOcrNoiseNormalized() {
        val raw = """
            โอนเงินสำเร็จ
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            นส. น้ำทิพย์ ตาทอง
            ธ.กสิกรไทย
            xxx-x-x5079-x
            จำนวน: 100.00 บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertTrue(draft.toName!!.contains("น้ำทิพย์"))
    }

    @Test
    fun kbankP2pRecipientFullSurnameNoSpaceAfterTitle() {
        val raw = """
            โอนเงินสำเร็จ
            28 ก.ค. 69 02:32 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            นายเกียรติศักดิ์ พิมพ์อาภรณ์
            ธ.กรุงไทย
            xxx-x-x9444-x
            เลขที่รายการ: 016209023213BOR02751
            จำนวน: 10.00 บาท
            ค่าธรรมเนียม: 0.00 บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("10.00", draft.amount)
        assertTrue(draft.fromName!!.contains("เกียรติศักดิ์ พ"))
        assertTrue(draft.toName!!.contains("พิมพ์อาภรณ์"))
        assertTrue(draft.toName!!.contains("xxx-x-x9444-x"))
    }

    @Test
    fun kbankBillerStorylog() {
        val raw = """
            จ่ายบิลสำเร็จ
            1 มิ.ย. 69 01:05 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            STORYLOG
            800000260601007414
            000QH4X62RXXG2E05Q
            เลขที่รายการ: 016152010501BPM17567
            จำนวน: 200.00 บาท
            ค่าธรรมเนียม: 0.00 บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("200.00", draft.amount)
        assertTrue(draft.toName!!.contains("STORYLOG"))
        assertTrue(draft.toName!!.contains("800000260601007414"))
    }

    @Test
    fun kbankBillerCounterServiceCompany() {
        val raw = """
            ชำระเงินสำเร็จ
            1 มิ.ย. 69 05:35 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            เคาน์เตอร์เซอร์วิส ไทยคิวอาร์ 00046
            บจก. เคาน์เตอร์เซอร์วิส
            202606010094017
            เลขที่รายการ: 016152053540BQR06365
            จำนวน: 882.00 บาท
            ค่าธรรมเนียม: 0.00 บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("882.00", draft.amount)
        assertTrue(draft.toName!!.contains("เคาน์เตอร์เซอร์วิส"))
        assertTrue(draft.toName!!.contains("บจก"))
        assertTrue(draft.toName!!.contains("202606010094017"))
    }

    @Test
    fun kbankBillerElementPayme() {
        val raw = """
            จ่ายบิลสำเร็จ
            5 มิ.ย. 69 21:50 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            ELEMENT PAYME
            42414173
            20359951
            เลขที่รายการ: 016156215017APM15360
            จำนวน: 2,000.00 บาท
            ค่าธรรมเนียม: 0.00 บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("2000.00", draft.amount)
        assertTrue(draft.toName!!.contains("ELEMENT PAYME"))
        assertTrue(draft.toName!!.contains("42414173"))
    }

    @Test
    fun kbankBillerThailandPostQr() {
        val raw = """
            จ่ายบิลสำเร็จ
            5 มิ.ย. 69 14:38 น.
            นาย เกียรติศักดิ์ พ
            ธ.กสิกรไทย
            xxx-x-x3523-x
            ระบบรับชำระคิวอาร์ ปณท
            150841
            000001098000048
            เลขที่รายการ: 016156143839DPM08775
            จำนวน: 40.00 บาท
            ค่าธรรมเนียม: 0.00 บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("40.00", draft.amount)
        assertTrue(draft.toName!!.contains("ปณท") || draft.toName!!.contains("ระบบรับชำระ"))
    }

    @Test
    fun makeByKbankPromptPay() {
        val raw = """
            โอนเงินสำเร็จ
            08 มิ.ย. 2569 01:43
            MAKE by KBank
            จาก
            วิชุฎา ส
            xxx-x-x3027-x
            ไปยัง
            ณัฐภัทร สาตรอด
            PromptPay
            จำนวน 500.00 บาท
            ค่าธรรมเนียม 0.00 บาท
            เลขที่รายการ 0461595o8sohrlvp5qhJ
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("500.00", draft.amount)
        assertEquals("KBank", draft.bank)
        assertEquals("2026-06-08T01:43:00+07:00", draft.spentAtIso)
        assertTrue(draft.fromName!!.contains("วิชุฎา ส"))
        assertTrue(draft.fromName!!.contains("xxx-x-x3027-x"))
        assertTrue(draft.toName!!.contains("ณัฐภัทร สาตรอด"))
    }
}
