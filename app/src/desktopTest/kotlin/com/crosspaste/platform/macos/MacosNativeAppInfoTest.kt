package com.crosspaste.platform.macos

import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@EnabledOnOs(OS.MAC)
class MacosNativeAppInfoTest {

    @Test
    fun `getAppInfoAtPath names a bundle like a running app`() {
        val info = MacAppUtils.getAppInfoAtPath("/System/Applications/Calculator.app")
        assertEquals("com.apple.calculator\nCalculator", info)
    }

    @Test
    fun `getAppInfoAtPath returns null for a plain directory`() {
        assertNull(MacAppUtils.getAppInfoAtPath("/System/Applications"))
    }

    @Test
    fun `saveAppIconAtPath renders a png for a bundle that is not running`() {
        val iconFile = File(Files.createTempDirectory("crosspaste-icon").toFile(), "Calculator.png")
        try {
            assertTrue(MacAppUtils.saveAppIconAtPath("/System/Applications/Calculator.app", iconFile.path))
            val header = iconFile.readBytes().take(8)
            assertEquals(listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).map { it.toByte() }, header)
        } finally {
            iconFile.parentFile.deleteRecursively()
        }
    }
}
