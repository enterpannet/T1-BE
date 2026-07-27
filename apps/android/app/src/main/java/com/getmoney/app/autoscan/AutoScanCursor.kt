package com.getmoney.app.autoscan

object AutoScanCursor {
    fun initialCursor(nowEpochSec: Long): Long = nowEpochSec
    fun advanceToScanStart(scanStartedAtEpochSec: Long): Long = scanStartedAtEpochSec
}
