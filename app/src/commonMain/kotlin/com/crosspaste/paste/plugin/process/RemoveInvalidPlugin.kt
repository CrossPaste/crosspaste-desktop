package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem

object RemoveInvalidPlugin : PasteProcessPlugin {
    override fun process(
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> = pasteItems.filter { it.isValid() }
}
