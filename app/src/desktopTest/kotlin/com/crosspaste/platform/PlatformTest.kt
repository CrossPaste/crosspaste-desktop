package com.crosspaste.platform

import com.crosspaste.utils.DesktopPlatformUtils
import com.crosspaste.utils.SystemProperty
import com.crosspaste.utils.getPlatformUtils
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformTest {

    private fun detect(
        osName: String,
        osVersion: String,
        osArch: String,
    ): Platform {
        val systemProperty = mockk<SystemProperty>()
        every { systemProperty.get("os.name") } returns osName
        every { systemProperty.get("os.version") } returns osVersion
        every { systemProperty.get("os.arch") } returns osArch
        return DesktopPlatformUtils.detectPlatform(systemProperty)
    }

    @Test
    fun testPlatform() {
        val platform = getPlatformUtils().platform
        assertTrue(platform.name != "Unknown")
    }

    @Test
    fun testDisplayName() {
        fun platform(name: String) = Platform(name = name, arch = "arm64", bitMode = 64, version = "1")

        assertEquals("macOS", platform(Platform.MACOS).displayName())
        assertEquals("Windows", platform(Platform.WINDOWS).displayName())
        assertEquals("Linux", platform(Platform.LINUX).displayName())
        assertEquals("iPhone", platform(Platform.IPHONE).displayName())
        assertEquals("Unknown", platform(Platform.UNKNOWN_OS).displayName())
    }

    @Test
    fun testIphoneDuoUsesReportedHardwareModel() {
        val duo = Platform(name = Platform.IPHONE, arch = "iPhone19,4", bitMode = 64, version = "27.1")

        assertTrue(duo.isIphoneDuo())
        assertFalse(duo.copy(arch = "iPhone19,2").isIphoneDuo())
        assertFalse(duo.copy(arch = "arm64").isIphoneDuo())
        assertFalse(duo.copy(name = Platform.IPAD).isIphoneDuo())
    }

    @Test
    fun testWindowsPlatform() {
        val platform = detect("Windows 10", "10.0", "amd64")
        assertEquals("Windows", platform.name)
        assertEquals("amd64", platform.arch)
        assertEquals(64, platform.bitMode)
        assertEquals("10", platform.version)
    }

    @Test
    fun testMacOSIntelPlatform() {
        val platform = detect("Mac OS X", "10.15.7", "x86_64")
        assertEquals("Macos", platform.name)
        assertEquals("x86_64", platform.arch)
        assertEquals(64, platform.bitMode)
        assertEquals("10.15.7", platform.version)
    }

    @Test
    fun testMacOSARMPlatform() {
        val platform = detect("Mac OS X", "11.2", "arm64")
        assertEquals("Macos", platform.name)
        assertEquals("arm64", platform.arch)
        assertEquals(64, platform.bitMode)
        assertEquals("11.2", platform.version)
    }

    @Test
    fun testLinuxPlatform() {
        // The Linux version comes from the host (lsb_release / /etc/os-release), not from
        // os.version, so it is not asserted here.
        val platform = detect("Linux", "6.5.0-35-generic", "x86_64")
        assertEquals("Linux", platform.name)
        assertEquals("x86_64", platform.arch)
        assertEquals(64, platform.bitMode)
    }

    @Test
    fun testBitModeFromArch() {
        assertEquals(32, detect("Windows 10", "10.0", "x86").bitMode)
    }
}
