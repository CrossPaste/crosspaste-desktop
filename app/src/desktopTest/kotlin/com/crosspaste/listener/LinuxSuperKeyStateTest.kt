package com.crosspaste.listener

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LinuxSuperKeyStateTest {
    private fun key(
        rawCode: Int,
        keyCode: Int,
        modifiers: Int = 0,
        id: Int = NativeKeyEvent.NATIVE_KEY_PRESSED,
    ): NativeKeyEvent = NativeKeyEvent(id, modifiers, rawCode, keyCode, NativeKeyEvent.CHAR_UNDEFINED)

    @Test
    fun `XWayland Super mapped to Yen still matches Shift Super Space`() {
        val state = LinuxSuperKeyState()
        val superKey = state.normalize(key(0xffeb, NativeKeyEvent.VC_YEN, NativeKeyEvent.SHIFT_L_MASK))
        assertEquals(NativeKeyEvent.VC_META, superKey.keyCode)
        assertEquals(NativeKeyEvent.KEY_LOCATION_LEFT, superKey.keyLocation)

        // These values reproduce the events captured on KDE Wayland: the hook
        // supplies SHIFT_MASK but omits META_MASK on the following Space event.
        val space = state.normalize(key(0x20, NativeKeyEvent.VC_SPACE, NativeKeyEvent.SHIFT_L_MASK))
        assertTrue(LinuxKeyboardKeys.SHIFT.match(space))
        assertTrue(LinuxKeyboardKeys.COMMAND.match(space))
        assertTrue(LinuxKeyboardKeys.SPACE.match(space))

        val release =
            state.normalize(key(0xffeb, NativeKeyEvent.VC_YEN, id = NativeKeyEvent.NATIVE_KEY_RELEASED))
        assertEquals(NativeKeyEvent.VC_META, release.keyCode)
        assertFalse(LinuxKeyboardKeys.COMMAND.match(state.normalize(key(0x20, NativeKeyEvent.VC_SPACE))))
    }

    @Test
    fun `right Super works when XFree86 maps it to undefined`() {
        val state = LinuxSuperKeyState()
        val event = state.normalize(key(0xffec, NativeKeyEvent.VC_UNDEFINED))
        assertEquals(NativeKeyEvent.VC_META, event.keyCode)
        assertEquals(NativeKeyEvent.KEY_LOCATION_RIGHT, event.keyLocation)
        assertEquals(NativeKeyEvent.META_R_MASK, event.modifiers)
    }

    @Test
    fun `releasing one Super does not release the other and repeats are idempotent`() {
        val state = LinuxSuperKeyState()
        repeat(2) { state.normalize(key(0xffeb, NativeKeyEvent.VC_YEN)) }
        state.normalize(key(0xffec, NativeKeyEvent.VC_UNDEFINED))
        state.normalize(key(0xffeb, NativeKeyEvent.VC_YEN, id = NativeKeyEvent.NATIVE_KEY_RELEASED))
        assertEquals(NativeKeyEvent.META_R_MASK, state.normalize(key(0x20, NativeKeyEvent.VC_SPACE)).modifiers)

        state.normalize(key(0xffec, NativeKeyEvent.VC_UNDEFINED, id = NativeKeyEvent.NATIVE_KEY_RELEASED))
        assertEquals(0, state.normalize(key(0x20, NativeKeyEvent.VC_SPACE)).modifiers)
    }

    @Test
    fun `real Yen and unrelated modifiers are preserved`() {
        val state = LinuxSuperKeyState()
        val yen = key(0xa5, NativeKeyEvent.VC_YEN)
        assertSame(yen, state.normalize(yen))
        state.normalize(key(0xffeb, NativeKeyEvent.VC_YEN))
        val original = key(0x20, NativeKeyEvent.VC_SPACE, NativeKeyEvent.CTRL_MASK or NativeKeyEvent.ALT_MASK)
        val corrected = state.normalize(original)
        assertEquals(original.modifiers or NativeKeyEvent.META_L_MASK, corrected.modifiers)
        assertEquals(original.keyCode, corrected.keyCode)
        assertEquals(original.keyLocation, corrected.keyLocation)
        assertEquals(original.rawCode, corrected.rawCode)
        assertEquals(original.keyChar, corrected.keyChar)
        assertEquals(NativeKeyEvent.CTRL_MASK or NativeKeyEvent.ALT_MASK, original.modifiers)
    }

    @Test
    fun `already correct hook events retain their modifier information`() {
        val state = LinuxSuperKeyState()
        val space = key(0x20, NativeKeyEvent.VC_SPACE, NativeKeyEvent.SHIFT_L_MASK or NativeKeyEvent.META_L_MASK)
        assertSame(space, state.normalize(space))
        val superKey = state.normalize(key(0xffeb, NativeKeyEvent.VC_META, NativeKeyEvent.META_L_MASK))
        assertEquals(NativeKeyEvent.VC_META, superKey.keyCode)
        assertEquals(NativeKeyEvent.META_L_MASK, superKey.modifiers)
    }
}
