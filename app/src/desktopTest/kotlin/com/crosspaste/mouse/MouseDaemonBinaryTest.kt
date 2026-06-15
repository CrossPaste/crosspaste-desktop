package com.crosspaste.mouse

import com.crosspaste.platform.Platform
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MouseDaemonBinaryTest {
    private val propKey = "crosspaste.mouse.binary"
    private val savedProp = System.getProperty(propKey)

    @AfterTest
    fun restore() {
        if (savedProp != null) System.setProperty(propKey, savedProp) else System.clearProperty(propKey)
    }

    @Test
    fun `system property wins when set and file exists`() {
        val file = Files.createTempFile("fake-daemon", "").toFile().apply { deleteOnExit() }
        System.setProperty(propKey, file.absolutePath)
        assertEquals(file.absolutePath, MouseDaemonBinary.resolve(envLookup = { null })?.absolutePath)
    }

    @Test
    fun `env var used when system property absent`() {
        System.clearProperty(propKey)
        val file = Files.createTempFile("fake-daemon", "").toFile().apply { deleteOnExit() }
        val path =
            MouseDaemonBinary.resolve(envLookup = {
                if (it ==
                    "CROSSPASTE_MOUSE_BIN"
                ) {
                    file.absolutePath
                } else {
                    null
                }
            })
        assertEquals(file.absolutePath, path?.absolutePath)
    }

    @Test
    fun `returns null when nothing resolves`() {
        System.clearProperty(propKey)
        assertNull(MouseDaemonBinary.resolve(envLookup = { null }, candidatePaths = emptyList()))
    }

    @Test
    fun `system property pointing at missing file is ignored`() {
        System.setProperty(propKey, "/definitely/does/not/exist/mouse")
        assertNull(MouseDaemonBinary.resolve(envLookup = { null }, candidatePaths = emptyList()))
    }

    @Test
    fun `binaryName appends exe on windows only`() {
        val windows = Platform(name = "Windows", arch = "x64", bitMode = 64, version = "10")
        val macos = Platform(name = "Macos", arch = "arm64", bitMode = 64, version = "14")
        val linux = Platform(name = "Linux", arch = "x64", bitMode = 64, version = "5.15")
        assertEquals("crosspaste-mouse.exe", MouseDaemonBinary.binaryName(windows))
        assertEquals("crosspaste-mouse", MouseDaemonBinary.binaryName(macos))
        assertEquals("crosspaste-mouse", MouseDaemonBinary.binaryName(linux))
    }

    @Test
    fun `candidate paths are used when overrides are absent`() {
        System.clearProperty(propKey)
        val file = Files.createTempFile("fake-daemon", "").toFile().apply { deleteOnExit() }
        val result =
            MouseDaemonBinary.resolve(
                candidatePaths = listOf("", "/does/not/exist", file.absolutePath),
                envLookup = { null },
            )
        assertEquals(file.absolutePath, result?.absolutePath)
    }
}
