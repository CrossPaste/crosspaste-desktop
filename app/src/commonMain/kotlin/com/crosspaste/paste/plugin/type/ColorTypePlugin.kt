package com.crosspaste.paste.plugin.type

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.PasteItem

interface ColorTypePlugin : PasteTypePlugin {

    suspend fun updateColor(
        pasteData: PasteData,
        newColor: Long,
        pasteItem: PasteItem,
        pasteDao: PasteDao,
    ): Result<ColorPasteItem>
}
