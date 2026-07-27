package com.getmoney.app.data.cloudinary

import com.google.gson.JsonObject
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

class CloudinaryUploader(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun upload(file: File): Result<String> = withContext(Dispatchers.IO) {
        if (!CloudinaryConfig.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Cloudinary not configured"))
        }
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody("image/jpeg".toMediaType()),
                )
                .addFormDataPart("upload_preset", CloudinaryConfig.uploadPreset)
                .build()

            val url =
                "https://api.cloudinary.com/v1_1/${CloudinaryConfig.cloudName}/image/upload"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IOException("Cloudinary upload failed: ${response.code}"),
                    )
                }
                val json = Gson().fromJson(body, JsonObject::class.java)
                val uploadedUrl = json.get("secure_url")?.asString
                    ?: json.get("url")?.asString
                    ?: return@withContext Result.failure(
                        IOException("Cloudinary response missing URL"),
                    )
                Result.success(uploadedUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
