package com.getmoney.app.autoscan

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoScanCursorLogicTest {
    @Test
    fun firstRunStartsAtBeginningOfHistory() {
        assertEquals(0L, AutoScanCursor.initialCursor())
        assertEquals(0L, AutoScanCursor.BEGINNING_OF_HISTORY)
    }

    @Test
    fun emptyBatchAdvancesToScanStart() {
        assertEquals(
            1_700_000_100L,
            AutoScanCursor.advanceAfterBatch(emptyList(), 1_700_000_100L),
        )
    }

    @Test
    fun nonEmptyBatchAdvancesToMaxDateAdded() {
        assertEquals(
            1_699_999_980L,
            AutoScanCursor.advanceAfterBatch(
                listOf(1_699_999_950L, 1_699_999_980L, 1_699_999_900L),
                1_700_000_100L,
            ),
        )
    }
}
