package com.clipevery.listen

import com.clipevery.listener.ShortcutKeysCore
import java.util.Properties

interface ShortcutKeysLoader {

    fun load(properties: Properties): ShortcutKeysCore
}
