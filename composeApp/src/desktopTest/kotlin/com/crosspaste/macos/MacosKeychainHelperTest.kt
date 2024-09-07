package com.crosspaste.macos

import com.crosspaste.platform.getPlatform
import com.crosspaste.platform.macos.MacosKeychainHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacosKeychainHelperTest {

    @Test
    fun testKeychain() {
        if (getPlatform().isMacos()) {
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
}
