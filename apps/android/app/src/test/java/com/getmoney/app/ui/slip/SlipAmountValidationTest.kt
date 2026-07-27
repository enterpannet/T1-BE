package com.getmoney.app.ui.slip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlipAmountValidationTest {
    @Test
    fun rejectsBlankAndZero() {
        assertFalse(isValidSlipAmount(""))
        assertFalse(isValidSlipAmount("   "))
        assertFalse(isValidSlipAmount("0"))
        assertFalse(isValidSlipAmount("0.00"))
    }

    @Test
    fun acceptsPositiveAmounts() {
        assertTrue(isValidSlipAmount("1"))
        assertTrue(isValidSlipAmount("1250.50"))
        assertTrue(isValidSlipAmount("1,250.50"))
    }
}
