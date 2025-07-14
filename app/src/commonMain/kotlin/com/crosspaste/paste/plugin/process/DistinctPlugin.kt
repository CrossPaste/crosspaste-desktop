package com.crosspaste.paste.plugin.process

import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider

class DistinctPlugin(
    userDataPathProvider: UserDataPathProvider,
) : PasteProcessPlugin {

    private val firstPlugin = FirstPlugin(userDataPathProvider)

    private val childPlugins =
        mapOf(
            Pair(
                PasteType.IMAGE_TYPE,
                MultiImagesPlugin(userDataPathProvider),
            ),
            Pair(
                PasteType.FILE_TYPE,
                MultiFilesPlugin(userDataPathProvider),
            ),
            Pair(PasteType.TEXT_TYPE, firstPlugin),
            Pair(PasteType.COLOR_TYPE, firstPlugin),
            Pair(PasteType.URL_TYPE, firstPlugin),
            Pair(PasteType.HTML_TYPE, firstPlugin),
            Pair(PasteType.RTF_TYPE, firstPlugin),
        )

    override fun process(
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> =
        pasteItems
            .groupBy { it.getPasteType() }
            .map { (pasteType, items) ->
                val plugin = childPlugins[pasteType]
                plugin?.process(pasteCoordinate, items, source) ?: items
            }.flatten()
}

class FirstPlugin(
    private val userDataPathProvider: UserDataPathProvider,
) : PasteProcessPlugin {
    override fun process(
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> =
        if (pasteItems.isEmpty()) {
            listOf()
        } else {
            for (pasteAppearItem in pasteItems.drop(1)) {
                pasteAppearItem.clear(
                    pasteCoordinate = pasteCoordinate,
                    userDataPathProvider = userDataPathProvider,
                )
            }
            listOf(pasteItems.first())
        }
}
