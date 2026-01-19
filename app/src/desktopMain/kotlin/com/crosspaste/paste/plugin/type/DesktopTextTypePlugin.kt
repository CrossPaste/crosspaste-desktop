package com.crosspaste.paste.plugin.type

import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.toPasteDataFlavor
import java.awt.datatransfer.DataFlavor

class DesktopTextTypePlugin : TextTypePlugin {

    companion object {

        const val UNICODE_STRING = "Unicode String"
        const val TEXT = "text/plain"
        const val PLAIN_TEXT = "Plain Text"
    }

    override fun getPasteType(): PasteType = PasteType.TEXT_TYPE

    override fun getIdentifiers(): List<String> = listOf(UNICODE_STRING, TEXT, PLAIN_TEXT)

    override fun createPrePasteItem(
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        createTextPasteItem(
            identifiers = listOf(identifier),
            text = "",
        ).let {
            pasteCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(
        transferData: Any,
        pasteId: Long,
        itemIndex: Int,
        dataFlavor: PasteDataFlavor,
        dataFlavorMap: Map<String, List<PasteDataFlavor>>,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        if (transferData is String) {
            val update: (PasteItem) -> PasteItem = { pasteItem ->
                createTextPasteItem(
                    identifiers = pasteItem.identifiers,
                    text = transferData,
                    extraInfo = pasteItem.extraInfo,
                )
            }
            pasteCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }

    override fun buildTransferable(
        pasteItem: PasteItem,
        mixedCategory: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
        pasteItem as TextPasteItem
        map[DataFlavor.stringFlavor.toPasteDataFlavor()] = pasteItem.text
    }
}
