package com.getmoney.app.autoscan

import android.net.Uri

data class ScannedImage(
    val uri: Uri,
    val dateAddedSec: Long,
    val bucketId: String?,
)
