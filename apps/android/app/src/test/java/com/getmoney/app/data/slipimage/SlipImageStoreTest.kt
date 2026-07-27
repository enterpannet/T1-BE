package com.getmoney.app.data.slipimage

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

    private val minimalJpeg = byteArrayOf(
        0xFF.toByte(),
        0xD8.toByte(),
        0xFF.toByte(),
        0xD9.toByte(),
    )

    @Test
    fun saveAndResolveRoundTrip() = runBlocking {
        val root = createTempDir()
        val store = SlipImageStore(root)
        val source = writeSourceFile(root, "source.png", minimalJpeg)

        val saved = store.saveFromUri(transactionId, source)

        assertNotNull(saved)
        assertTrue(saved!!.exists())
        assertEquals(saved, store.fileFor(transactionId))
        assertEquals("tx-abc-123.jpg", saved.name)
        assertTrue(saved.parentFile!!.name == "slips")
        assertContentEquals(minimalJpeg, saved.readBytes())
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
        val source = writeSourceFile(root, "source.jpg", minimalJpeg)
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
        val first = writeSourceFile(root, "first.jpg", minimalJpeg)
        val secondBytes = byteArrayOf(0x01, 0x02, 0x03)
        val second = writeSourceFile(root, "second.jpg", secondBytes)

        store.saveFromUri(transactionId, first)
        store.saveFromUri(transactionId, second)

        assertContentEquals(secondBytes, store.fileFor(transactionId)!!.readBytes())
    }

    private fun writeSourceFile(root: File, name: String, bytes: ByteArray): Uri {
        val file = root.resolve(name)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    private fun assertContentEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index ->
            assertEquals(expected[index], actual[index])
        }
    }
}
