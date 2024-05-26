package com.clipevery.listener

interface ShortcutKeysListener {

    var editShortcutKeysMode: Boolean

    var currentKeys: MutableList<KeyboardKey>
}
