package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payloads captured from the real corpus with `python tools/probe_slip_qr.py`.
 *
 * Thai e-slips carry two very different QR codes and they must not be confused:
 *  - the bank's slip-verification QR, which identifies *this* transaction, and
 *  - the account holder's own PromptPay QR, printed so someone can transfer
 *    money *to* them later.
 */
class SlipQrReferenceTest {

    private val kplusTransferQr =
        "0041000600000101030040220016152010501BPM175675102TH9104CC7D"

    /** Top-up slips drop the three-letter type code. */
    private val kplusTopUpQr =
        "00390006000001010300402180161521916546013445102TH910487ED"

    private val promptPayAccountQr =
        "00020101021129390016A00000067701011103150049990063143055303764" +
            "5802TH63043B80"

    @Test
    fun readsTransactionIdFromSlipVerificationQr() {
        assertEquals("016152010501BPM17567", EmvQrParser.extractSlipReference(kplusTransferQr))
        assertEquals("016152191654601344", EmvQrParser.extractSlipReference(kplusTopUpQr))
    }

    @Test
    fun slipVerificationQrIsNotMistakenForPromptPay() {
        assertFalse(EmvQrParser.isPromptPayQr(kplusTransferQr))
        assertFalse(EmvQrParser.isPromptPayQr(kplusTopUpQr))
    }

    @Test
    fun promptPayAccountQrYieldsNoTransactionReference() {
        assertTrue(EmvQrParser.isPromptPayQr(promptPayAccountQr))
        assertNull(EmvQrParser.extractSlipReference(promptPayAccountQr))
    }

    /**
     * The verification QR is the authority for the reference: OCR reads the
     * letter block of `016…BOR02751` as digits, and a reference stored as
     * `B0R` cannot be looked up with the bank.
     */
    @Test
    fun qrReferenceDisagreesWithOcrExactlyWhereOcrConfusesLetterOAndZero() {
        val fromQr = EmvQrParser.extractSlipReference(
            "0041000600000101030040220016209023213BOR027515102TH9104AAAA"
        )
        assertEquals("016209023213BOR02751", fromQr)
        assertFalse("QR must not carry the OCR spelling", fromQr!!.contains("B0R"))
    }
}
