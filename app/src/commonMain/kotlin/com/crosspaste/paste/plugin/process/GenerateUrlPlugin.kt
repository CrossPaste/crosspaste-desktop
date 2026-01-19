package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.utils.getUrlUtils

object GenerateUrlPlugin : PasteProcessPlugin {

    private val urlUtils = getUrlUtils()

    override fun process(
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> {
        if (pasteItems.all { it !is UrlPasteItem }) {
            pasteItems.filterIsInstance<TextPasteItem>().firstOrNull()?.let {
                if (urlUtils.isValidUrl(it.text)) {
                    return pasteItems +
                        createUrlPasteItem(
                            identifiers = it.identifiers,
                            url = it.text,
                        )
                }
            }
        }
        return pasteItems
    }
}
