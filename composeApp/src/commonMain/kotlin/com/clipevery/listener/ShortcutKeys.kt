package com.clipevery.listener

interface ShortcutKeys {

    var shortcutKeysCore: ShortcutKeysCore

    fun update(
        keyName: String,
        keys: List<KeyboardKey>,
    )
}
