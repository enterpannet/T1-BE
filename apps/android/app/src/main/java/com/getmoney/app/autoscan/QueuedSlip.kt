package com.getmoney.app.autoscan

import android.net.Uri
import com.getmoney.app.ocr.SlipDraft
import com.getmoney.app.ocr.SlipIntake

data class QueuedSlip(
    val uri: Uri,
    val draft: SlipDraft,
    val source: SlipIntake.Source,
)
