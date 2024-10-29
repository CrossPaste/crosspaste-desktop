package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.utils.getUrlUtils
import io.realm.kotlin.MutableRealm

object GenerateUrlPlugin : PasteProcessPlugin {

    private val urlUtils = getUrlUtils()

    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
        source: String?,
    ): List<PasteItem> {
        if (pasteItems.all { it !is UrlPasteItem }) {
            pasteItems.filterIsInstance<TextPasteItem>().firstOrNull()?.let {
                if (urlUtils.isValidUrl(it.text)) {
                    return pasteItems +
                        UrlPasteItem().apply {
                            this.identifier = it.identifier
                            this.url = it.text
                            this.size = it.size
                            this.hash = it.hash
                        }
                }
            }
        }
        return pasteItems
    }
}
