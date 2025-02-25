package com.crosspaste.listener

interface ShortcutKeysListener {

    var editShortcutKeysMode: Boolean

    var currentKeys: MutableList<KeyboardKey>
}
