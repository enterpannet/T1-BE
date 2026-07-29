plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun loadDotEnv(file: java.io.File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val idx = line.indexOf('=')
            line.substring(0, idx).trim() to line.substring(idx + 1).trim().trim('"')
        }
}

val apiEnv: Map<String, String> = loadDotEnv(rootProject.file("../api/.env"))

fun envOrProp(name: String, vararg aliases: String): String {
    val fromProp = project.findProperty(name) as String?
    if (!fromProp.isNullOrBlank()) return fromProp
    val fromEnv = apiEnv[name]
    if (!fromEnv.isNullOrBlank()) return fromEnv
    for (alias in aliases) {
        val value = apiEnv[alias]
        if (!value.isNullOrBlank()) return value
    }
    return ""
}

android {
    namespace = "com.getmoney.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.getmoney.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 53
        versionName = "1.52"
        buildConfigField("String", "API_BASE_URL", "\"https://tmd.deals/\"")
        val cloudinaryCloudName = envOrProp("CLOUDINARY_CLOUD_NAME")
        val cloudinaryUploadPreset = envOrProp("CLOUDINARY_UPLOAD_PRESET", "UPLOAD_PRESET")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"$cloudinaryCloudName\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"$cloudinaryUploadPreset\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"https://tmd.deals/\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "API_BASE_URL", "\"https://tmd.deals/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "traineddata"
        noCompress += "onnx"
        noCompress += "yml"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.6")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // QR: ML Kit barcode. Slip text: PaddleOCR Thai (primary) + Tesseract fallback.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation(project(":ppocr-sdk"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.21.1")
    implementation("com.quickbirdstudios:opencv:4.5.3")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    // Reads tools/dump_slip_ocr.py output in SlipDateAccuracyTest.
    testImplementation("com.google.code.gson:gson:2.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
