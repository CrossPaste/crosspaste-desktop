package com.crosspaste.platform.windows

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsAppNameTest {

    @Test
    fun `fallback strips the exe suffix case-insensitively`() {
        assertEquals(
            "Bitwarden",
            WindowsAppNames.fallbackAppName("C:/Program Files/Bitwarden/Bitwarden.exe".toPath()),
        )
        assertEquals("tool", WindowsAppNames.fallbackAppName("/apps/tool.EXE".toPath()))
    }

    @Test
    fun `fallback keeps names without an exe suffix`() {
        assertEquals("launcher", WindowsAppNames.fallbackAppName("/apps/launcher".toPath()))
    }
}
