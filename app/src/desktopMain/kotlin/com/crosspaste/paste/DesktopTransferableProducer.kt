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
            builder.add(it, pasteItem, mixedCategory = false)
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
        primary: Boolean,
    ): DesktopWriteTransferable? {
        val builder = DesktopWriteTransferableBuilder()

        val pasteAppearItems = pasteData.getPasteAppearItems()

        val pasteAppearItem = pasteAppearItems.firstOrNull() ?: return null

        val isFileCategory = pasteAppearItem is PasteFiles

        // Reverse so the primary item (first in pasteAppearItems) is added last to the
        // LinkedHashMap-backed builder, giving its DataFlavors the highest priority for
        // clipboard consumers that pick the last supported flavor.
        val itemsToProcess =
            if (primary) {
                pasteAppearItems.reversed().filter { (it is PasteFiles) == isFileCategory }
            } else {
                pasteAppearItems.reversed()
            }

        val mixedCategory = !primary

        for (item in itemsToProcess) {
            pasteTypePluginMap[item.getPasteType()]?.let {
                builder.add(it, item, mixedCategory = mixedCategory)
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
}
