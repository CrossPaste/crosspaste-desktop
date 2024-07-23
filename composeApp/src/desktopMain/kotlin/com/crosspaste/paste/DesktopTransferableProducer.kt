package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteData
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.plugin.type.PasteTypePlugin
import java.awt.datatransfer.DataFlavor

class DesktopTransferableProducer(
    pasteTypePlugins: List<PasteTypePlugin>,
) : TransferableProducer {

    private val pasteTypePluginMap: Map<Int, PasteTypePlugin> =
        pasteTypePlugins.associateBy { it.getPasteType() }

    override fun produce(
        pasteData: PasteData,
        localOnly: Boolean,
        filterFile: Boolean,
    ): DesktopWriteTransferable? {
        val builder = DesktopWriteTransferableBuilder()

        val pasteAppearItems = pasteData.getPasteAppearItems()

        for (pasteAppearItem in pasteAppearItems.reversed()) {
            if (filterFile) {
                if (pasteAppearItem is PasteFiles) {
                    continue
                }
            } else {
                pasteTypePluginMap[pasteAppearItem.getPasteType()]?.let {
                    builder.add(it, pasteAppearItem)
                }
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
