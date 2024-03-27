package com.clipevery.clip

import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipData
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class DesktopTransferableProducer: TransferableProducer {
    override fun produce(clipData: ClipData, localOnly: Boolean, filterFile: Boolean): Transferable? {
        val map = LinkedHashMap<DataFlavor, Any>()

        val clipAppearItems = clipData.getClipAppearItems()

        for (clipAppearItem in clipAppearItems) {
            if (filterFile) {
                if (clipAppearItem is ClipFiles) {
                    continue
                }
            } else {
                clipAppearItem.fillDataFlavor(map)
            }
        }

        return if (map.isEmpty()) {
            null
        } else {
            if (localOnly) {
                map[LocalOnlyFlavor] = true
            }

            ClipTransferable(map)
        }
    }
}

object LocalOnlyFlavor: DataFlavor("application/x-local-only-flavor;class=java.lang.Boolean", "Local Only Flavor") {
    private fun readResolve(): Any = LocalOnlyFlavor
}