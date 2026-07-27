package com.getmoney.app.autoscan

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoScanCursorLogicTest {
    @Test
    fun firstRunUsesNowWithoutScanningPast() {
        assertEquals(1_700_000_000L, AutoScanCursor.initialCursor(1_700_000_000L))
    }

    @Test
    fun afterScanCursorEqualsScanStart() {
        assertEquals(1_700_000_100L, AutoScanCursor.advanceToScanStart(1_700_000_100L))
    }
}
