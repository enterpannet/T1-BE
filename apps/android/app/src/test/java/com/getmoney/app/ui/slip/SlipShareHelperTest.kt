package com.getmoney.app.ui.slip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlipShareHelperTest {
    @Test
    fun sanitizeAddsJpgWhenMissing() {
        assertEquals("1607.jpg", SlipShareHelper.sanitizeFileName("1607"))
    }

    @Test
    fun captionIncludesFileParsedAndOcr() {
        val caption = SlipShareHelper.buildDebugCaption(
            fileName = "1607.jpg",
            amount = "69.00",
            bank = "KBank",
            reference = "REF1",
            fromName = "นาย A",
            toName = "น.ส. B",
            rawOcrText = "โอนเงินสำเร็จ\n16 ก.ค. 69\nจำนวน 200 บาท",
        )
        assertTrue(caption.contains("file: 1607.jpg"))
        assertTrue(caption.contains("amount: 69.00"))
        assertTrue(caption.contains("from: นาย A"))
        assertTrue(caption.contains("--- OCR ---"))
        assertTrue(caption.contains("จำนวน 200 บาท"))
    }
}
