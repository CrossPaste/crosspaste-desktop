package com.crosspaste.listener

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent

/**
 * X11 keysyms identify Super independently of libuiohook's scancode table.
 * JNativeHook 2.2.2 selects the XFree86 table when XkbGetKeyboard fails on
 * XWayland: Super_L becomes VC_YEN and neither Super key sets META_MASK.
 * Normalize Super at the Linux input boundary, including the modifier state
 * on subsequent keys. The shortcut dispatcher and editor share these events.
 * Key events and timeout resets can arrive on different threads.
 */
internal class LinuxSuperKeyState {
    private var heldModifiers = 0

    @Synchronized
    fun reset() {
        heldModifiers = 0
    }

    @Synchronized
    fun normalize(event: NativeKeyEvent): NativeKeyEvent {
        val mask =
            when (event.rawCode) {
                XK_SUPER_L -> NativeKeyEvent.META_L_MASK
                XK_SUPER_R -> NativeKeyEvent.META_R_MASK
                else -> 0
            }
        if (mask != 0) {
            heldModifiers =
                when (event.id) {
                    NativeKeyEvent.NATIVE_KEY_PRESSED -> heldModifiers or mask
                    NativeKeyEvent.NATIVE_KEY_RELEASED -> heldModifiers and mask.inv()
                    else -> heldModifiers
                }
        }

        val modifiers = event.modifiers or heldModifiers
        if (mask == 0 && modifiers == event.modifiers) return event

        return NativeKeyEvent(
            event.id,
            modifiers,
            event.rawCode,
            if (mask != 0) NativeKeyEvent.VC_META else event.keyCode,
            event.keyChar,
            when (mask) {
                NativeKeyEvent.META_L_MASK -> NativeKeyEvent.KEY_LOCATION_LEFT
                NativeKeyEvent.META_R_MASK -> NativeKeyEvent.KEY_LOCATION_RIGHT
                else -> event.keyLocation
            },
        )
    }

    private companion object {
        const val XK_SUPER_L = 0xffeb
        const val XK_SUPER_R = 0xffec
    }
}
