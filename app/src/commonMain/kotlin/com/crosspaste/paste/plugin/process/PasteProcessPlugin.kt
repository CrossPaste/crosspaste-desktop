package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem

interface PasteProcessPlugin {

    fun process(
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem>
}
