package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.utils.getColorUtils

object TextToColorPlugin : PasteProcessPlugin {

    private val colorUtils = getColorUtils()

    override fun process(
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> {
        if (pasteItems.all { it !is ColorPasteItem }) {
            pasteItems.filterIsInstance<TextPasteItem>().firstOrNull()?.let {
                colorUtils.tryCovertToColor(it.text)?.let { color ->
                    return pasteItems +
                        ColorPasteItem(
                            identifiers = it.identifiers,
                            hash = color.toString(),
                            size = 8L,
                            color = color,
                        )
                }
            }
        }
        return pasteItems
    }
}
