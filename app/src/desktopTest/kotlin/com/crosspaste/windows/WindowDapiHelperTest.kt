package com.crosspaste.windows

import com.crosspaste.platform.DesktopPlatformProvider
import com.crosspaste.platform.windows.WindowDapiHelper
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowDapiHelperTest {

    @Test
    fun testDapi() {
        val currentPlatform = DesktopPlatformProvider().getPlatform()
        assumeTrue(currentPlatform.isWindows(), "Test requires Windows")

        val str = "test windows dapi"
        val encryptData = WindowDapiHelper.encryptData(str.encodeToByteArray())
        assertTrue { (encryptData?.size ?: 0) > 0 }
        val decryptData = WindowDapiHelper.decryptData(encryptData!!)
        assertTrue { (decryptData?.size ?: 0) > 0 }
        assertEquals(str, String(decryptData!!))
    }
}
