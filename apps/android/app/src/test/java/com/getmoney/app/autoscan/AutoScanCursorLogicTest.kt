package com.getmoney.app.autoscan

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoScanCursorLogicTest {
    private val now = 1_700_000_100L
    private val sevenDays = 7L * 24L * 60L * 60L

    @Test
    fun beginningOfHistoryIsZero() {
        assertEquals(0L, AutoScanCursor.initialCursor())
        assertEquals(0L, AutoScanCursor.BEGINNING_OF_HISTORY)
    }

    @Test
    fun lookbackFloorIsSevenDaysBeforeNow() {
        assertEquals(now - sevenDays, AutoScanCursor.lookbackFloorEpochSec(now))
    }

    @Test
    fun effectiveCursorNullUsesLookbackFloor() {
        assertEquals(now - sevenDays, AutoScanCursor.effectiveCursor(null, now))
    }

    @Test
    fun effectiveCursorAncientUsesLookbackFloor() {
        assertEquals(now - sevenDays, AutoScanCursor.effectiveCursor(0L, now))
        assertEquals(now - sevenDays, AutoScanCursor.effectiveCursor(1_000L, now))
    }

    @Test
    fun effectiveCursorWithinWindowKeepsStored() {
        val recent = now - 1L * 24L * 60L * 60L
        assertEquals(recent, AutoScanCursor.effectiveCursor(recent, now))
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
