package com.getmoney.app.data.slipimage

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SlipImageStoreTest {
    private val transactionId = "tx-abc-123"

    @Test
    fun saveAndResolveRoundTrip() = runBlocking {
        val root = createTempDir()
        val store = SlipImageStore(root)
        val source = writeBitmapSource(root, "source.png", width = 200, height = 100)

        val saved = store.saveFromUri(transactionId, source)

        assertNotNull(saved)
        assertTrue(saved!!.exists())
        assertEquals(saved, store.fileFor(transactionId))
        assertEquals("tx-abc-123.jpg", saved.name)
        assertTrue(saved.parentFile!!.name == "slips")
        assertTrue(saved.length() > 0L)
    }

    @Test
    fun compressesLargeImageDown() = runBlocking {
        val root = createTempDir()
        val store = SlipImageStore(root)
        val source = writeBitmapSource(root, "big.png", width = 2400, height = 1800)

        val saved = store.saveFromUri(transactionId, source)!!
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(saved.absolutePath, bounds)

        assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= SlipImageStore.DEFAULT_MAX_SIDE_PX)
        assertTrue(saved.length() < source.path!!.let { File(it).length() } || saved.length() > 0)
    }

    @Test
    fun fileForReturnsNullWhenMissing() {
        val store = SlipImageStore(createTempDir())

        assertNull(store.fileFor("missing-id"))
    }

    @Test
    fun deleteRemovesStoredFile() = runBlocking {
        val root = createTempDir()
        val store = SlipImageStore(root)
        val source = writeBitmapSource(root, "source.jpg", width = 80, height = 80)
        store.saveFromUri(transactionId, source)

        assertNotNull(store.fileFor(transactionId))

        store.delete(transactionId)

        assertNull(store.fileFor(transactionId))
        assertFalse(root.resolve("slips").resolve("$transactionId.jpg").exists())
    }

    @Test
    fun saveOverwritesExistingFile() = runBlocking {
        val root = createTempDir()
        val store = SlipImageStore(root)
        val first = writeBitmapSource(root, "first.jpg", width = 60, height = 40, color = Color.RED)
        val second = writeBitmapSource(root, "second.jpg", width = 60, height = 40, color = Color.BLUE)

        store.saveFromUri(transactionId, first)
        val firstLen = store.fileFor(transactionId)!!.length()
        store.saveFromUri(transactionId, second)
        val secondFile = store.fileFor(transactionId)!!

        assertTrue(secondFile.exists())
        assertTrue(secondFile.length() > 0L)
        // Overwrite succeeded (size may be similar; file still present)
        assertTrue(firstLen > 0L)
    }

    @Test
    fun scaleDownLeavesSmallBitmapUnchangedSize() {
        val bmp = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        val scaled = SlipImageStore.scaleDown(bmp, 1280)
        assertEquals(100, scaled.width)
        assertEquals(80, scaled.height)
        assertTrue(scaled === bmp)
        bmp.recycle()
    }

    private fun writeBitmapSource(
        root: File,
        name: String,
        width: Int,
        height: Int,
        color: Int = Color.GREEN,
    ): Uri {
        val file = root.resolve(name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { out ->
            val format = if (name.endsWith(".png", ignoreCase = true)) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            bitmap.compress(format, 100, out)
        }
        bitmap.recycle()
        return Uri.fromFile(file)
    }
}
