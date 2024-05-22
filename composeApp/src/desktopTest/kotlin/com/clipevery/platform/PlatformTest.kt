package com.clipevery.platform

import com.clipevery.utils.DesktopSystemProperty
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun testPlatform() {
        val platform = currentPlatform()
        assertTrue(platform.name != "Unknown")
    }

    @Test
    fun testWindowsPlatform() {
        try {
            mockkObject(DesktopSystemProperty)
            every { DesktopSystemProperty.get("os.name") } returns "Windows 10"
            every { DesktopSystemProperty.get("os.version") } returns "10.0"
            every { DesktopSystemProperty.get("os.arch") } returns "amd64"

            val platform = currentPlatform()
            assertEquals("Windows", platform.name)
            assertEquals("amd64", platform.arch)
            assertEquals(64, platform.bitMode)
            assertEquals("10", platform.version)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun testMacOSIntelPlatform() {
        mockkObject(DesktopSystemProperty)
        try {
            every { DesktopSystemProperty.get("os.name") } returns "Mac OS X"
            every { DesktopSystemProperty.get("os.version") } returns "10.15.7"
            every { DesktopSystemProperty.get("os.arch") } returns "x86_64"

            val platform = currentPlatform()
            assertEquals("Macos", platform.name)
            assertEquals("x86_64", platform.arch)
            assertEquals(64, platform.bitMode)
            assertEquals("10.15.7", platform.version)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun testMacOSARMPlatform() {
        mockkObject(DesktopSystemProperty)
        try {
            every { DesktopSystemProperty.get("os.name") } returns "Mac OS X"
            every { DesktopSystemProperty.get("os.version") } returns "11.2"
            every { DesktopSystemProperty.get("os.arch") } returns "arm64"

            val platform = currentPlatform()
            assertEquals("Macos", platform.name)
            assertEquals("arm64", platform.arch)
            assertEquals(64, platform.bitMode)
            assertEquals("11.2", platform.version)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun testLinuxPlatform() {
        mockkObject(DesktopSystemProperty)
        try {
            every { DesktopSystemProperty.get("os.name") } returns "Linux"
            every { DesktopSystemProperty.get("os.version") } returns "6.5.0-35-generic"
            every { DesktopSystemProperty.get("os.arch") } returns "x86_64"

            val platform = currentPlatform()
            assertEquals("Linux", platform.name)
            assertEquals("x86_64", platform.arch)
            assertEquals(64, platform.bitMode)
            assertEquals("6.5.0-35-generic", platform.version)
        } finally {
            unmockkAll()
        }
    }
}
