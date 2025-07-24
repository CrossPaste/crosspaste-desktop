package com.crosspaste.paste

import com.crosspaste.db.paste.PasteData
import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.plugin.type.PasteTypePlugin
import java.awt.datatransfer.DataFlavor

class DesktopTransferableProducer(
    pasteTypePlugins: List<PasteTypePlugin>,
) : TransferableProducer {

    private val pasteTypePluginMap: Map<PasteType, PasteTypePlugin> =
        pasteTypePlugins.associateBy { it.getPasteType() }

    override fun produce(
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

    override fun produce(
        pasteData: PasteData,
        localOnly: Boolean,
        primary: Boolean,
    ): DesktopWriteTransferable? {
        val builder = DesktopWriteTransferableBuilder()

        val pasteAppearItems = pasteData.getPasteAppearItems()

        val pasteAppearItem = pasteAppearItems.firstOrNull()

        if (pasteAppearItem == null) {
            return null
        }

        val isFileCategory = pasteAppearItem is PasteFiles

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

object LocalOnlyFlavor : DataFlavor("application/x-local-only-flavor;class=java.lang.Boolean", "Local Only Flavor") {
    private fun readResolve(): Any = LocalOnlyFlavor
}
