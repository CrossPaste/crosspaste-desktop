package com.crosspaste.macos

import com.crosspaste.platform.macos.MacosKeychainHelper
import com.crosspaste.utils.getPlatformUtils
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacosKeychainHelperTest {

    @Test
    fun testKeychain() {
        val platform = getPlatformUtils().platform
        assumeTrue(platform.isMacos(), "Test requires macOS")

        MacosKeychainHelper.setPassword("com.crosspaste", "test", "test")
        var password = MacosKeychainHelper.getPassword("com.crosspaste", "test")
        assertEquals("test", password)
        MacosKeychainHelper.updatePassword("com.crosspaste", "test", "test1")
        password = MacosKeychainHelper.getPassword("com.crosspaste", "test")
        assertEquals("test1", password)
        assertTrue(MacosKeychainHelper.deletePassword("com.crosspaste", "test"))
        val password1 = MacosKeychainHelper.getPassword("com.crosspaste", "test")
        assertEquals(null, password1)
    }
}
