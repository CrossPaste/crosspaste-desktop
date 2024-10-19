package com.crosspaste.paste.plugin.processs

import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.realm.paste.PasteItem
import io.realm.kotlin.MutableRealm
import java.net.MalformedURLException
import java.net.URL

object GenerateUrlPlugin : PasteProcessPlugin {

    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
        source: String?,
    ): List<PasteItem> {
        if (pasteItems.all { it !is UrlPasteItem }) {
            pasteItems.filterIsInstance<TextPasteItem>().firstOrNull()?.let {
                if (isUrl(it.text)) {
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

    private fun isUrl(string: String): Boolean {
        try {
            URL(string)
            return true
        } catch (_: MalformedURLException) {
            return false
        }
    }
}
