package com.clipevery.clip

import com.clipevery.app.AppInfo
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

abstract class ClipItemService(protected val appInfo: AppInfo) {

    abstract fun getIdentifiers(): List<String>

    abstract fun createPreClipItem(
        clipId: Int,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
        clipCollector: ClipCollector
    )

    fun loadRepresentation(
        clipId: Int,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        try {
            val transferData = transferable.getTransferData(dataFlavor)
            doLoadRepresentation(transferData, clipId, itemIndex, dataFlavor, dataFlavorMap, transferable, clipCollector)
        } catch (e: Exception) {
            collectError(e, clipId, itemIndex, clipCollector)
        }
    }

    abstract fun doLoadRepresentation(transferData: Any,
                                      clipId: Int,
                                      itemIndex: Int,
                                      dataFlavor: DataFlavor,
                                      dataFlavorMap: Map<String, List<DataFlavor>>,
                                      transferable: Transferable,
                                      clipCollector: ClipCollector)

    fun collectError(error: Exception,
                     clipId: Int,
                     itemIndex: Int,
                     clipCollector: ClipCollector) {
        clipCollector.collectError(clipId, itemIndex, error)
    }
}