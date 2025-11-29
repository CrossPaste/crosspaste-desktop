package com.crosspaste.listener

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent

interface ShortcutKeysAction {

    val actioning: Boolean

    val event: NativeKeyEvent?

    val action: (String, NativeKeyEvent) -> Unit
}
