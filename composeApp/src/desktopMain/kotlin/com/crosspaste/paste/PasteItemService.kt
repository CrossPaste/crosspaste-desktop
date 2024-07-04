package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

abstract class PasteItemService(protected val appInfo: AppInfo) {

    abstract fun getIdentifiers(): List<String>

    abstract fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
        pasteCollector: PasteCollector,
    )

    fun loadRepresentation(
        pasteId: Long,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        pasteCollector: PasteCollector,
    ) {
        try {
            val transferData = transferable.getTransferData(dataFlavor)
            doLoadRepresentation(transferData, pasteId, itemIndex, dataFlavor, dataFlavorMap, transferable, pasteCollector)
        } catch (e: Exception) {
            collectError(e, pasteId, itemIndex, pasteCollector)
        }
    }

    abstract fun doLoadRepresentation(
        transferData: Any,
        pasteId: Long,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        pasteCollector: PasteCollector,
    )

    fun collectError(
        error: Exception,
        pasteId: Long,
        itemIndex: Int,
        pasteCollector: PasteCollector,
    ) {
        pasteCollector.collectError(pasteId, itemIndex, error)
    }
}
