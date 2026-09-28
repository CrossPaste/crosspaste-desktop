package com.crosspaste.paste

import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.plugin.type.PasteTypePlugin

class DesktopTransferableProducer(
    pasteTypePlugins: List<PasteTypePlugin>,
) : TransferableProducer {

    private val pasteTypePluginMap: Map<PasteType, PasteTypePlugin> =
        pasteTypePlugins.associateBy { it.getPasteType() }

    override suspend fun produce(
        pasteItem: PasteItem,
        localOnly: Boolean,
    ): DesktopWriteTransferable? {
        val builder = DesktopWriteTransferableBuilder()

        pasteTypePluginMap[pasteItem.getPasteType()]?.let {
            builder.add(it, pasteItem, PasteWriteScope.PRIMARY_CATEGORY)
        }

        return if (builder.isEmpty()) {
            null
        } else {
            if (localOnly) {
                builder.add(LocalOnlyFlavor.toPasteDataFlavor(), true)
            }
            builder.build()
        }
    }

    override suspend fun produce(
        pasteData: PasteData,
        localOnly: Boolean,
        scope: PasteWriteScope,
    ): DesktopWriteTransferable? {
        val builder = DesktopWriteTransferableBuilder()

        val pasteAppearItems = pasteData.getPasteAppearItems()

        val primaryItem = pasteAppearItems.firstOrNull() ?: return null

        val itemsToWrite =
            when (scope) {
                PasteWriteScope.PRIMARY_CATEGORY -> pasteAppearItems.filter { it.category() == primaryItem.category() }
                PasteWriteScope.ALL -> pasteAppearItems
            }

        // Written in reverse so the primary item lands last: when two items put a
        // value under the same flavor, the primary item's is the one that stays.
        for (item in itemsToWrite.reversed()) {
            pasteTypePluginMap[item.getPasteType()]?.let {
                builder.add(it, item, scope)
            }
        }

        return if (builder.isEmpty()) {
            null
        } else {
            if (localOnly) {
                builder.add(LocalOnlyFlavor.toPasteDataFlavor(), true)
            }
            builder.build()
        }
    }

    /** The two categories a paste write keeps apart: files, and everything else. */
    private enum class Category { FILES, CONTENT }

    private fun PasteItem.category(): Category = if (this is PasteFiles) Category.FILES else Category.CONTENT
}
