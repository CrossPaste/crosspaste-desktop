package com.crosspaste.platform

import com.crosspaste.platform.LinuxPlatform
import com.crosspaste.utils.DesktopSystemProperty
import com.crosspaste.utils.getPlatformUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun testPlatform() {
        val platform = getPlatformUtils().platform
        assertTrue(platform.name != "Unknown")
    }

    @Test
    fun testWindowsPlatform() {
        runCatching {
            mockkObject(DesktopSystemProperty)
            every { DesktopSystemProperty.get("os.name") } returns "Windows 10"
            every { DesktopSystemProperty.get("os.version") } returns "10.0"
            every { DesktopSystemProperty.get("os.arch") } returns "amd64"

            val platform = getPlatformUtils().platform
            assertEquals("Windows", platform.name)
            assertEquals("amd64", platform.arch)
            assertEquals(64, platform.bitMode)
            assertEquals("10", platform.version)
        }.apply {
            unmockkAll()
        }
    }

    @Test
    fun testMacOSIntelPlatform() {
        mockkObject(DesktopSystemProperty)
        runCatching {
            every { DesktopSystemProperty.get("os.name") } returns "Mac OS X"
            every { DesktopSystemProperty.get("os.version") } returns "10.15.7"
            every { DesktopSystemProperty.get("os.arch") } returns "x86_64"

            val platform = getPlatformUtils().platform
            assertEquals("Macos", platform.name)
            assertEquals("x86_64", platform.arch)
            assertEquals(64, platform.bitMode)
            assertEquals("10.15.7", platform.version)
        }.apply {
            unmockkAll()
        }
    }

    @Test
    fun testMacOSARMPlatform() {
        mockkObject(DesktopSystemProperty)
        runCatching {
            every { DesktopSystemProperty.get("os.name") } returns "Mac OS X"
            every { DesktopSystemProperty.get("os.version") } returns "11.2"
            every { DesktopSystemProperty.get("os.arch") } returns "arm64"

            val platform = getPlatformUtils().platform
            assertEquals("Macos", platform.name)
            assertEquals("arm64", platform.arch)
            assertEquals(64, platform.bitMode)
            assertEquals("11.2", platform.version)
        }.apply {
            unmockkAll()
        }
    }

    @Test
    fun testLinuxPlatform() {
        mockkObject(DesktopSystemProperty)
        runCatching {
            every { DesktopSystemProperty.get("os.name") } returns "Linux"
            every { DesktopSystemProperty.get("os.version") } returns "6.5.0-35-generic"
            every { DesktopSystemProperty.get("os.arch") } returns "x86_64"

            val platform = getPlatformUtils().platform
            assertEquals("Linux", platform.name)
            assertEquals("x86_64", platform.arch)
            assertEquals(64, platform.bitMode)
            assertEquals(LinuxPlatform.getOsVersion(), platform.version)
        }.apply {
            unmockkAll()
        }
    }
}
