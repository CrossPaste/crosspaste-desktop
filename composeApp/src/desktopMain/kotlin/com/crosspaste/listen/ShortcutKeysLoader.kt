package com.crosspaste.listen

import com.crosspaste.listener.ShortcutKeysCore
import java.util.Properties

interface ShortcutKeysLoader {

    fun load(properties: Properties): ShortcutKeysCore
}
