package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteItem

interface PasteTypePlugin {

    fun getPasteType(): Int

    fun getIdentifiers(): List<String>

    fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    )

    fun loadRepresentation(
        pasteId: Long,
        itemIndex: Int,
        dataFlavor: PasteDataFlavor,
        dataFlavorMap: Map<String, List<PasteDataFlavor>>,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        try {
            val transferData = pasteTransferable.getTransferData(dataFlavor)
            doLoadRepresentation(transferData, pasteId, itemIndex, dataFlavor, dataFlavorMap, pasteTransferable, pasteCollector)
        } catch (e: Exception) {
            collectError(e, pasteId, itemIndex, pasteCollector)
        }
    }

    fun doLoadRepresentation(
        transferData: Any,
        pasteId: Long,
        itemIndex: Int,
        dataFlavor: PasteDataFlavor,
        dataFlavorMap: Map<String, List<PasteDataFlavor>>,
        pasteTransferable: PasteTransferable,
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

    fun buildTransferable(
        pasteItem: PasteItem,
        map: MutableMap<PasteDataFlavor, Any>,
    )
}
