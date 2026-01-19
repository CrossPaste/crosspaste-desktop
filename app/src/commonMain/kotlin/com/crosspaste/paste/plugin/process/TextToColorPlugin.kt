package com.crosspaste.paste.plugin.process

import androidx.compose.ui.graphics.toArgb
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.utils.getColorUtils

object TextToColorPlugin : PasteProcessPlugin {

    private val colorUtils = getColorUtils()

    override fun process(
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> {
        if (pasteItems.all { it !is ColorPasteItem }) {
            pasteItems.filterIsInstance<TextPasteItem>().firstOrNull()?.let {
                colorUtils.toColor(it.text)?.let { color ->
                    return pasteItems +
                        createColorPasteItem(
                            identifiers = it.identifiers,
                            color = color.toArgb(),
                        )
                }
            }
        }
        return pasteItems
    }
}
