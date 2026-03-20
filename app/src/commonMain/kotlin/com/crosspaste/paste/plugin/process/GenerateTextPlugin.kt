package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.paste.item.TextPasteItem

class GenerateTextPlugin(
    private val pasteItemReader: PasteItemReader,
) : PasteProcessPlugin {
    override fun process(
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> {
        if (pasteItems.any { it is HtmlPasteItem } &&
            pasteItems.all { it !is TextPasteItem }
        ) {
            pasteItems.filterIsInstance<HtmlPasteItem>().firstOrNull()?.let {
                val text = pasteItemReader.getText(it)
                if (text.isEmpty()) return pasteItems
                return pasteItems +
                    createTextPasteItem(
                        text = text,
                    )
            }
        }

        if (pasteItems.any { it is RtfPasteItem } &&
            pasteItems.all { it !is TextPasteItem }
        ) {
            pasteItems.filterIsInstance<RtfPasteItem>().firstOrNull()?.let {
                val text = pasteItemReader.getText(it)
                if (text.isEmpty()) return pasteItems
                return pasteItems +
                    createTextPasteItem(
                        text = text,
                    )
            }
        }

        return pasteItems
    }
}
