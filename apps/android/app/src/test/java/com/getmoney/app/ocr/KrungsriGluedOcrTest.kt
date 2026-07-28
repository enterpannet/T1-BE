package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures from RapidOCR on real Krungsri / K+ slips that failed date + payee. */
class KrungsriGluedOcrTest {
    @Test
    fun gluedUjDateAndTrueMoney() {
        val d = SlipParser.parse(
            """
            krungsri
            28U.J.256915:04:48
            KIATTISAKPIM
            XXX-1-73041-X
            nsuud (e-Wallet)
            truemoney
            wallet
            XXX-0-02065-X
            1,000.00 THB
            KSA00000000662048389
            """.trimIndent(),
        )
        assertEquals("2026-06-28T15:04:48+07:00", d.spentAtIso)
        assertTrue(d.toName!!.contains("ทรู") || d.toName!!.contains("e-Wallet", ignoreCase = true))
        assertFalse(d.fromName!!.contains("truemoney", ignoreCase = true))
    }

    @Test
    fun gluedDotsDateUsesYymmddMerchantRef() {
        val d = SlipParser.parse(
            """
            Scan to Pay สำเร็จ
            krungsri
            18..256917:41:00
            KIATTISAKPIM
            XXX-1-73041-X
            010-556-2-XXXX286-0
            153.13 THB
            MerchantID
            KGP00031A000001
            Transaction Reference
            260618174054AINVWK0S
            KSA00000000633125562
            """.trimIndent(),
        )
        assertEquals("2026-06-18T17:40:54+07:00", d.spentAtIso)
        assertTrue(
            "to=${d.toName}",
            d.toName!!.contains("010-556") || d.toName!!.contains("XXXX286"),
        )
    }

    @Test
    fun gluedNumericMonth() {
        val d = SlipParser.parse(
            """
            krungsri
            20.8.256909:18:27
            KIATTISAKPIM
            XXX-1-73041-X
            XXX-0-39444-X
            1,000.00 THB
            KSA00000000637310692
            """.trimIndent(),
        )
        assertEquals("2026-08-20T09:18:27+07:00", d.spentAtIso)
        // Note: real slip is June; OCR read month as .8. — numeric path still recovers clock
    }

    @Test
    fun advancedMpayPayee() {
        val d = SlipParser.parse(
            """
            krungsri
            18U.J.256910:31:12
            KIATTISAKPIM
            XXX-1-73041-X
            XXX-0-05620-X
            299.00 THB
            AdvancedmPAY
            AWNGOMO
            KS0000000631948894
            """.trimIndent(),
        )
        assertEquals("2026-06-18T10:31:12+07:00", d.spentAtIso)
        assertTrue(d.toName!!.contains("แอดวานซ์") || d.toName!!.contains("mPAY", ignoreCase = true))
    }

    @Test
    fun alternateMaskAccountXDash() {
        val d = SlipParser.parse(
            """
            โอนเงินสำเร็จ
            krungsri
            29 U.J. 2569 21:27:31
            KIATTISAK PIM
            XXX-1-73041-X
            X-XXXX-XXXX1-17-3
            50.00 THB
            KSA00000000666337452
            """.trimIndent(),
        )
        assertEquals("2026-06-29T21:27:31+07:00", d.spentAtIso)
        assertTrue(
            "to=${d.toName}",
            d.toName!!.contains("X-XXXX", ignoreCase = true) ||
                d.toName!!.contains("XXXX") ||
                d.toName!!.contains("17-3"),
        )
    }

    @Test
    fun kplusTrueMoneyViaEleven() {
        val d = SlipParser.parse(
            """
            เติมเงินสำเร็จ
            K+
            2 1.A. 69 19:10 H.
            XXX-X-x3523-x
            ELEVEN
            01350548260702071019
            26070219101992536606
            016183191043130636
            200.00
            """.trimIndent(),
        )
        assertEquals("2026-07-02T19:10:43+07:00", d.spentAtIso)
        assertTrue(
            "to=${d.toName}",
            d.toName!!.contains("ทรู") || d.toName!!.contains("ELEVEN", ignoreCase = true),
        )
    }
}
