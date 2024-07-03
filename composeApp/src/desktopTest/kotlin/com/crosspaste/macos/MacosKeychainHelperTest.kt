package com.crosspaste.macos

import com.crosspaste.os.macos.MacosKeychainHelper
import com.crosspaste.platform.currentPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacosKeychainHelperTest {

    @Test
    fun testKeychain() {
        if (currentPlatform().isMacos()) {
            MacosKeychainHelper.setPassword("com.crosspaste", "test", "test")
            var password = MacosKeychainHelper.getPassword("com.crosspaste", "test")
            assertEquals("test", password)
            MacosKeychainHelper.updatePassword("com.crosspaste", "test", "test1")
            password = MacosKeychainHelper.getPassword("com.crosspaste", "test")
            assertEquals("test1", password)
            assertTrue(MacosKeychainHelper.deletePassword("com.crosspaste", "test"))
            val password1 = MacosKeychainHelper.getPassword("com.crosspaste", "test")
            assertTrue(password1 == null)
        }
    }
}
