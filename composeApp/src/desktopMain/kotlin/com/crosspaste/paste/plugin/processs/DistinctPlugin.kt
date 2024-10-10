package com.crosspaste.paste.plugin.processs

import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteType
import io.realm.kotlin.MutableRealm

class DistinctPlugin(userDataPathProvider: UserDataPathProvider) : PasteProcessPlugin {

    private val firstPlugin = FirstPlugin(userDataPathProvider)

    private val childPlugins =
        mapOf(
            Pair(PasteType.IMAGE, MultiImagesPlugin(userDataPathProvider)),
            Pair(PasteType.FILE, MultFilesPlugin(userDataPathProvider)),
            Pair(PasteType.TEXT, firstPlugin),
            Pair(PasteType.URL, firstPlugin),
            Pair(PasteType.HTML, firstPlugin),
        )

    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
        source: String?,
    ): List<PasteItem> {
        return pasteItems.groupBy { it.getPasteType() }.map { (pasteType, items) ->
            val plugin = childPlugins[pasteType]
            plugin?.process(items, realm, source) ?: items
        }.flatten()
    }
}

class FirstPlugin(private val userDataPathProvider: UserDataPathProvider) : PasteProcessPlugin {
    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
        source: String?,
    ): List<PasteItem> {
        return if (pasteItems.isEmpty()) {
            listOf()
        } else {
            for (pasteAppearItem in pasteItems.drop(1)) {
                pasteAppearItem.clear(realm, userDataPathProvider)
            }
            listOf(pasteItems.first())
        }
    }
}
