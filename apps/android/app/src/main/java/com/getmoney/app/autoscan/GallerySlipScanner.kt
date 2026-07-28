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
        if (extraBucketIds.isEmpty() || limit <= 0) return@withContext emptyList()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
        )
        val placeholders = extraBucketIds.joinToString(",") { "?" }
        val selection =
            "${MediaStore.Images.Media.DATE_ADDED} > ? AND " +
                "${MediaStore.Images.Media.BUCKET_ID} IN ($placeholders)"
        val selectionArgs = arrayOf(afterEpochSec.toString()) + extraBucketIds.map { it }.toTypedArray()
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

    override suspend fun countImages(bucketIds: Set<String>): Int = withContext(Dispatchers.IO) {
        if (bucketIds.isEmpty()) return@withContext 0
        val placeholders = bucketIds.joinToString(",") { "?" }
        val selection = "${MediaStore.Images.Media.BUCKET_ID} IN ($placeholders)"
        val selectionArgs = bucketIds.toTypedArray()
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            null,
        )?.use { cursor -> cursor.count } ?: 0
    }

    override suspend fun countImagesAfter(
        bucketIds: Set<String>,
        afterEpochSec: Long,
    ): Int = withContext(Dispatchers.IO) {
        if (bucketIds.isEmpty()) return@withContext 0
        val placeholders = bucketIds.joinToString(",") { "?" }
        val selection =
            "${MediaStore.Images.Media.DATE_ADDED} > ? AND " +
                "${MediaStore.Images.Media.BUCKET_ID} IN ($placeholders)"
        val selectionArgs = arrayOf(afterEpochSec.toString()) + bucketIds.toTypedArray()
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            null,
        )?.use { cursor -> cursor.count } ?: 0
    }

    suspend fun listBuckets(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val seen = linkedSetOf<String>()
        val results = mutableListOf<Pair<String, String>>()

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC",
        )?.use { cursor ->
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val displayNameCol =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext() && results.size < 100) {
                val bucketId = cursor.getString(bucketIdCol) ?: continue
                if (!seen.add(bucketId)) continue
                val displayName = cursor.getString(displayNameCol)?.takeIf { it.isNotBlank() }
                    ?: bucketId
                results.add(bucketId to displayName)
            }
        }
        results
    }
}
