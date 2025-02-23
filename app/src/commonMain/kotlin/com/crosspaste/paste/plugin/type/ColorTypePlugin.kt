package com.crosspaste.paste.plugin.type

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.PasteItem

interface ColorTypePlugin : PasteTypePlugin {

    fun updateColor(
        id: Long,
        newColor: Long,
        pasteItem: PasteItem,
        pasteDao: PasteDao,
    )
}
