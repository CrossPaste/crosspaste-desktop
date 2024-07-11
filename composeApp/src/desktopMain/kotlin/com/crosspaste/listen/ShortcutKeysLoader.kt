package com.crosspaste.listen

import com.crosspaste.listener.ShortcutKeysCore
import okio.Path

interface ShortcutKeysLoader {

    fun load(platformName: String): ShortcutKeysCore

    fun load(path: Path): ShortcutKeysCore
}
