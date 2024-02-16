package com.clipevery.clip

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

interface ClipItemService {

    fun getIdentifiers(): List<String>

    fun createClipItem(
        clipId: Int,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        try {
            val transferData = transferable.getTransferData(dataFlavor)
            doCreateClipItem(transferData, clipId, itemIndex, dataFlavor, transferable, clipCollector)
        } catch (e: Exception) {
            collectError(e, clipId, itemIndex, clipCollector)
        }
    }

    fun doCreateClipItem(transferData: Any,
                         clipId: Int,
                         itemIndex: Int,
                         dataFlavor: DataFlavor,
                         transferable: Transferable,
                         clipCollector: ClipCollector)

    fun collectError(error: Exception,
                     clipId: Int,
                     itemIndex: Int,
                     clipCollector: ClipCollector) {
        clipCollector.collectError(clipId, itemIndex, error)
    }
}