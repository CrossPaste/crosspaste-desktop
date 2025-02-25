package com.crosspaste.paste.plugin.type

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.PasteItem

interface TextTypePlugin : PasteTypePlugin {

    fun updateText(
        id: Long,
        newText: String,
        size: Long,
        hash: String,
        pasteItem: PasteItem,
        pasteDao: PasteDao,
    ): PasteItem
}
