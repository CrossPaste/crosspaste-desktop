package com.crosspaste.listener

import okio.Path

interface ShortcutKeysLoader {

    fun load(platformName: String): ShortcutKeysCore

    fun load(path: Path): ShortcutKeysCore
}
