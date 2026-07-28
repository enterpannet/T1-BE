package com.getmoney.app.update

import com.getmoney.app.data.api.AppVersionResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun detectsNewerVersionCode() {
        val remote = AppVersionResponse(
            versionCode = 50,
            versionName = "1.49",
            apkUrl = "https://tmd.deals/releases/x.apk",
        )
        assertTrue(AppUpdate.isNewer(remote, localVersionCode = 49))
        assertFalse(AppUpdate.isNewer(remote, localVersionCode = 50))
        assertFalse(AppUpdate.isNewer(remote, localVersionCode = 51))
    }
}
