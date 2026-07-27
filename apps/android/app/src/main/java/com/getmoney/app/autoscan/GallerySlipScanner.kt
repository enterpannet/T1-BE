package com.getmoney.app.autoscan

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GallerySlipScanner(context: Context) : GallerySlipScannerReader {
    private val contentResolver = context.contentResolver

    override suspend fun listNewImages(
        afterEpochSec: Long,
        extraBucketIds: Set<String>,
        limit: Int,
    ): List<ScannedImage> = withContext(Dispatchers.IO) {
        // Wide-first: extraBucketIds are persisted for Settings; query stays wide.

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
        )
        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(afterEpochSec.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} ASC"

        val results = mutableListOf<ScannedImage>()
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)

            while (cursor.moveToNext() && results.size < limit) {
                val id = cursor.getLong(idCol)
                val dateAddedSec = cursor.getLong(dateAddedCol)
                val bucketId = cursor.getString(bucketIdCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
                results.add(
                    ScannedImage(
                        uri = uri,
                        dateAddedSec = dateAddedSec,
                        bucketId = bucketId,
                    ),
                )
            }
        }
        results
    }
}
