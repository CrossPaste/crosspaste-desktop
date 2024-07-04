package com.crosspaste.paste.plugin

import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.paste.PastePlugin
import io.realm.kotlin.MutableRealm

object DistinctPlugin : PastePlugin {

    private val childPlugins =
        mapOf(
            Pair(PasteType.IMAGE, MultiImagesPlugin),
            Pair(PasteType.FILE, MultFilesPlugin),
            Pair(PasteType.TEXT, FirstPlugin),
            Pair(PasteType.URL, FirstPlugin),
            Pair(PasteType.HTML, FirstPlugin),
        )

    override fun pluginProcess(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem> {
        return pasteItems.groupBy { it.getPasteType() }.map { (pasteType, items) ->
            val plugin = childPlugins[pasteType]
            plugin?.pluginProcess(items, realm) ?: items
        }.flatten()
    }
}

object FirstPlugin : PastePlugin {
    override fun pluginProcess(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem> {
        return if (pasteItems.isEmpty()) {
            listOf()
        } else {
            for (pasteAppearItem in pasteItems.drop(1)) {
                pasteAppearItem.clear(realm)
            }
            listOf(pasteItems.first())
        }
    }
}
