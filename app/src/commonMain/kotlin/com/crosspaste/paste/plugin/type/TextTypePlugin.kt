package com.crosspaste.paste.plugin.type

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.item.PasteItem

interface TextTypePlugin : PasteTypePlugin {

    fun updateText(
        pasteData: PasteData,
        newText: String,
        pasteItem: PasteItem,
        pasteDao: PasteDao,
    ): PasteItem
}
