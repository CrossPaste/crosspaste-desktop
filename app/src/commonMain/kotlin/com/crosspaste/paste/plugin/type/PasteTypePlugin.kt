package com.crosspaste.paste.plugin.type

import com.crosspaste.paste.NoneTransferData
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteWriteScope
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

    // identity: unused on Desktop, required by Android/iOS to resolve platform-specific representations
    suspend fun loadRepresentation(
        pasteId: Long,
        itemIndex: Int,
        identity: String,
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
                    identity,
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

    suspend fun doLoadRepresentation(
        transferData: Any,
        pasteId: Long,
        itemIndex: Int,
        identity: String,
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

    /**
     * Puts the item's representations on the transferable. [scope] tells the
     * plugin whether items of the other category are written alongside.
     */
    suspend fun buildTransferable(
        pasteItem: PasteItem,
        scope: PasteWriteScope,
        map: MutableMap<PasteDataFlavor, Any>,
    )
}
