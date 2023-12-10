package com.clipevery.macos

import com.clipevery.os.macos.MacosKeychainHelper
import com.clipevery.platform.currentPlatform
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacosKeychainHelperTest {

    @Test
    fun testKeychain() {
        if (currentPlatform().isMacos()) {
            MacosKeychainHelper.setPassword("com.clipevery", "test", "test")
            var password = MacosKeychainHelper.getPassword("com.clipevery", "test")
            assertEquals("test", password)
            MacosKeychainHelper.updatePassword("com.clipevery", "test", "test1")
            password = MacosKeychainHelper.getPassword("com.clipevery", "test")
            assertEquals("test1", password)
            assertTrue(MacosKeychainHelper.deletePassword("com.clipevery", "test"))
            val password1 = MacosKeychainHelper.getPassword("com.clipevery", "test")
            assertTrue(password1 == null)
        }
    }
}