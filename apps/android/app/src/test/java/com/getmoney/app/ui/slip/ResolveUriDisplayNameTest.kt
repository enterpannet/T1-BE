package com.getmoney.app.ui.slip

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ResolveUriDisplayNameTest {
    @Test
    fun fileUriUsesLastPathSegment() {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("file:///storage/emulated/0/Download/016158070348ATF02045.jpeg")
        assertEquals("016158070348ATF02045.jpeg", resolveUriDisplayName(context, uri))
    }

    @Test
    fun bareNumericNameGetsJpgExtension() {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("file:///storage/emulated/0/Pictures/1607")
        assertEquals("1607.jpg", resolveUriDisplayName(context, uri))
    }
}
