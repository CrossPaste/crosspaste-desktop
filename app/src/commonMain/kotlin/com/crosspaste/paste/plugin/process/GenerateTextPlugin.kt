package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.paste.item.TextPasteItem

object GenerateTextPlugin : PasteProcessPlugin {
    override fun process(
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> {
        if (pasteItems.any { it is HtmlPasteItem } &&
            pasteItems.all { it !is TextPasteItem }
        ) {
            pasteItems.filterIsInstance<HtmlPasteItem>().firstOrNull()?.let {
                val text = it.getText()
                return pasteItems +
                    TextPasteItem.createTextPasteItem(
                        text = text,
                    )
            }
        }

        if (pasteItems.any { it is RtfPasteItem } &&
            pasteItems.all { it !is TextPasteItem }
        ) {
            pasteItems.filterIsInstance<RtfPasteItem>().firstOrNull()?.let {
                val text = it.getText()
                return pasteItems +
                    TextPasteItem.createTextPasteItem(
                        text = text,
                    )
            }
        }

        return pasteItems
    }
}
