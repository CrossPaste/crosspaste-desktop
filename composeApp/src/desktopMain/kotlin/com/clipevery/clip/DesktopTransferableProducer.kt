package com.clipevery.clip

import com.clipevery.dao.clip.ClipData
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class DesktopTransferableProducer: TransferableProducer {
    override fun produce(clipData: ClipData): Transferable {
        val map = LinkedHashMap<DataFlavor, Any>()

        val clipAppearItems = clipData.getClipAppearItems()

        for (clipAppearItem in clipAppearItems) {
            clipAppearItem.fillDataFlavor(map)
        }
        return ClipTransferable(map)
    }
}