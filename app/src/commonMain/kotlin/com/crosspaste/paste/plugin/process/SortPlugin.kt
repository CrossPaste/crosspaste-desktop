package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.PasteItem

object SortPlugin : PasteProcessPlugin {

    override fun process(
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> {
        return pasteItems.sortedByDescending { it.getPasteType().priority }
    }
}
