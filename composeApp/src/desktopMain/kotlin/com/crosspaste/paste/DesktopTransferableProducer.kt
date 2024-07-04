package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteData
import com.crosspaste.paste.item.PasteFiles
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class DesktopTransferableProducer : TransferableProducer {
    override fun produce(
        pasteData: PasteData,
        localOnly: Boolean,
        filterFile: Boolean,
    ): Transferable? {
        val map = LinkedHashMap<DataFlavor, Any>()

        val pasteAppearItems = pasteData.getPasteAppearItems()

        for (pasteAppearItem in pasteAppearItems.reversed()) {
            if (filterFile) {
                if (pasteAppearItem is PasteFiles) {
                    continue
                }
            } else {
                pasteAppearItem.fillDataFlavor(map)
            }
        }

        return if (map.isEmpty()) {
            null
        } else {
            if (localOnly) {
                map[LocalOnlyFlavor] = true
            }

            PasteTransferable(map)
        }
    }
}

object LocalOnlyFlavor : DataFlavor("application/x-local-only-flavor;class=java.lang.Boolean", "Local Only Flavor") {
    private fun readResolve(): Any = LocalOnlyFlavor
}
