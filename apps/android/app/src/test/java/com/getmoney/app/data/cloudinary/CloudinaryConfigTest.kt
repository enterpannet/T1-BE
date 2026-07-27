package com.getmoney.app.data.cloudinary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudinaryConfigTest {
    @Test
    fun isConfiguredRequiresBoth() {
        assertFalse(CloudinaryConfig.isConfiguredFor("", "preset"))
        assertFalse(CloudinaryConfig.isConfiguredFor("cloud", ""))
        assertTrue(CloudinaryConfig.isConfiguredFor("cloud", "preset"))
    }
}
