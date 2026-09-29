package com.crosspaste.platform.linux.api

import kotlin.test.Test
import kotlin.test.assertEquals

class PortalHandlesTest {

    @Test
    fun `sender strips the leading colon and swaps dots for underscores`() {
        assertEquals("1_42", PortalHandles.sender(":1.42"))
        assertEquals("1_42_7", PortalHandles.sender(":1.42.7"))
    }

    @Test
    fun `request and session paths follow the portal layout`() {
        assertEquals(
            "/org/freedesktop/portal/desktop/request/1_42/crosspaste_3",
            PortalHandles.requestPath(":1.42", "crosspaste_3"),
        )
        assertEquals(
            "/org/freedesktop/portal/desktop/session/1_42/crosspaste_4",
            PortalHandles.sessionPath(":1.42", "crosspaste_4"),
        )
    }

    @Test
    fun `x11 keycodes map to evdev by dropping the 8 offset`() {
        // LinuxKeyboardKeys: Ctrl = 37, V = 55 → KEY_LEFTCTRL = 29, KEY_V = 47
        assertEquals(29, PortalHandles.evdevKeycode(37))
        assertEquals(47, PortalHandles.evdevKeycode(55))
    }

    @Test
    fun `evdev keycode is non-negative even for codes below the offset`() {
        assertEquals(0, PortalHandles.evdevKeycode(8))
        assertEquals(0, PortalHandles.evdevKeycode(5))
        assertEquals(0, PortalHandles.evdevKeycode(0))
    }
}
