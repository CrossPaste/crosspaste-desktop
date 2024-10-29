package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteType
import io.realm.kotlin.MutableRealm
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class RemoveHtmlImagePlugin(
    private val userDataPathProvider: UserDataPathProvider,
) : PasteProcessPlugin {
    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
        source: String?,
    ): List<PasteItem> {
        if (pasteItems.any { it.getPasteType() == PasteType.IMAGE }) {
            pasteItems.firstOrNull { it.getPasteType() == PasteType.HTML }?.let { htmlItem ->
                val pasteHtml = htmlItem as PasteHtml
                val html = pasteHtml.html
                if (isSingleImgInBody(html)) {
                    htmlItem.clear(realm, userDataPathProvider, clearResource = true)
                    return pasteItems.filter { item -> item != htmlItem }
                }
            }
        }
        return pasteItems
    }

    private fun isSingleImgInBody(html: String): Boolean {
        val document: Document = Jsoup.parse(html)
        val body = document.body()
        val children = body.children()
        return children.size == 1 && children.first()?.tagName() == "img"
    }
}
