package com.crosspaste.mouse

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
}
