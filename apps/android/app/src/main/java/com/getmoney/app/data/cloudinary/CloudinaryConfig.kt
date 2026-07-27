package com.getmoney.app.data.cloudinary

import com.getmoney.app.BuildConfig

object CloudinaryConfig {
    val cloudName: String = BuildConfig.CLOUDINARY_CLOUD_NAME
    val uploadPreset: String = BuildConfig.CLOUDINARY_UPLOAD_PRESET
    val isConfigured: Boolean = isConfiguredFor(cloudName, uploadPreset)

    fun isConfiguredFor(name: String, preset: String): Boolean =
        name.isNotBlank() && preset.isNotBlank()
}
