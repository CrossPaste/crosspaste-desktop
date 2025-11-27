package com.crosspaste.listener

interface ShortcutKeysAction {

    val actioning: Boolean

    val action: (String) -> Unit
}
