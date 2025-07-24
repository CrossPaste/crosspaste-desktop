package com.crosspaste.paste.plugin.type

import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.NoneTransferData
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.PasteItem

interface PasteTypePlugin {

    fun getPasteType(): PasteType

    fun getIdentifiers(): List<String>

    fun createPrePasteItem(
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
        runCatching {
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
        }.onFailure {
            collectError(it, pasteId, itemIndex, pasteCollector)
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
        error: Throwable,
        pasteId: Long,
        itemIndex: Int,
        pasteCollector: PasteCollector,
    ) {
        pasteCollector.collectError(pasteId, itemIndex, error)
    }

    fun buildTransferable(
        pasteItem: PasteItem,
        mixedCategory: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    )
}
