package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EmvQrParserTest {
    @Test
    fun parsesAmountFromEmvPayload() {
        // Minimal EMV-like payload with tag 54 amount 350.00 and tag 53 currency 764
        val payload =
            "000201" +
                "010212" +
                "5303764" +
                "5406350.00" +
                "5802TH" +
                "5913Test Merchant" +
                "62070503ABC" +
                "6304ABCD"

        val result = EmvQrParser.parse(payload)
        assertNotNull(result)
        assertEquals("350.00", result!!.amount)
        assertEquals("764", result.currency)
        assertEquals("ABC", result.reference)
        assertEquals("Test Merchant", result.merchantName)
    }

    @Test
    fun parsesAmountFromVerifyUrl() {
        val url = "https://verify.example.com/slip?amount=1%2C250.50&ref=SCB2026XYZ"
        val result = EmvQrParser.parse(url)
        assertNotNull(result)
        assertEquals("1250.50", result!!.amount)
        assertEquals("SCB2026XYZ", result.reference)
    }

    @Test
    fun rejectsNonQrNoise() {
        assertNull(EmvQrParser.parse("just some thai text จำนวนเงิน 100"))
    }

    @Test
    fun toSlipDraftRequiresAmount() {
        val withoutAmount = EmvQrParser.Result(reference = "REF", rawPayload = "x")
        assertNull(EmvQrParser.toSlipDraft(withoutAmount))
    }
}
