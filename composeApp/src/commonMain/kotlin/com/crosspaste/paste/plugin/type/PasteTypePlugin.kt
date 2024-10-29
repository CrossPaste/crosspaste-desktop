package com.crosspaste.paste.plugin.type

import com.crosspaste.paste.NoneTransferData
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.realm.paste.PasteItem

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
            if (transferData != NoneTransferData) {
                doLoadRepresentation(
                    transferData,
                    pasteId,
                    itemIndex,
                    dataFlavor,
                    dataFlavorMap,
                    pasteTransferable,
                    pasteCollector,
                )
            }
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
        singleType: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    )
}
