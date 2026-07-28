package com.getmoney.app.ocr

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Copies bundled tessdata into `filesDir/tesseract/tessdata`.
 * TessBaseAPI expects datapath = parent of `tessdata/`.
 */
object TessDataInstaller {
    /** Bump when bundled traineddata changes so devices re-copy assets. */
    private const val ASSET_VERSION = "3"

    private val trainedDataFiles = listOf("tha.traineddata", "eng.traineddata")

    fun ensureDataPath(context: Context): String {
        val base = File(context.filesDir, "tesseract")
        val tessdata = File(base, "tessdata")
        if (!tessdata.exists()) {
            tessdata.mkdirs()
        }
        val marker = File(base, "asset-version.txt")
        val installed = marker.takeIf { it.exists() }?.readText()?.trim()
        if (installed != ASSET_VERSION) {
            trainedDataFiles.forEach { name ->
                File(tessdata, name).delete()
            }
            marker.writeText(ASSET_VERSION)
        }
        for (name in trainedDataFiles) {
            val out = File(tessdata, name)
            if (out.exists() && out.length() > 0L) continue
            context.assets.open("tessdata/$name").use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
        }
        return base.absolutePath
    }
}
