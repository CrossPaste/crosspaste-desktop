package com.crosspaste.paste.plugin

import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.paste.PastePlugin
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import io.realm.kotlin.MutableRealm
import java.net.MalformedURLException
import java.net.URL

object GenerateUrlPlugin : PastePlugin {

    override fun pluginProcess(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem> {
        if (pasteItems.all { it !is UrlPasteItem }) {
            pasteItems.filterIsInstance<TextPasteItem>().firstOrNull()?.let {
                if (isUrl(it.text)) {
                    return pasteItems +
                        UrlPasteItem().apply {
                            this.identifier = it.identifier
                            this.url = it.text
                            this.size = it.size
                            this.md5 = it.md5
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
        } catch (e: MalformedURLException) {
            return false
        }
    }
}
