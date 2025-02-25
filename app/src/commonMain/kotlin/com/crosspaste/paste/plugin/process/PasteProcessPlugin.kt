package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.PasteItem

interface PasteProcessPlugin {

    fun process(
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem>
}
