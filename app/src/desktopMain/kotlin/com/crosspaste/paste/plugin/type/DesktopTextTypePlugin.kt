package com.crosspaste.paste.plugin.type

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.utils.getCodecsUtils
import java.awt.datatransfer.DataFlavor

class DesktopTextTypePlugin(
    private val searchContentService: SearchContentService,
) : TextTypePlugin {

    companion object {

        const val UNICODE_STRING = "Unicode String"
        const val TEXT = "text/plain"
        const val PLAIN_TEXT = "Plain Text"

        private val codecsUtils = getCodecsUtils()
    }

    override fun getPasteType(): PasteType = PasteType.TEXT_TYPE

    override fun getIdentifiers(): List<String> = listOf(UNICODE_STRING, TEXT, PLAIN_TEXT)

    override fun createPrePasteItem(
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        TextPasteItem(
            identifiers = listOf(identifier),
            hash = "",
            size = 0,
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
            val textBytes = transferData.encodeToByteArray()
            val hash = codecsUtils.hash(textBytes)
            val update: (PasteItem) -> PasteItem = { pasteItem ->
                buildNewPasteItem(transferData, textBytes.size.toLong(), hash, pasteItem)
            }
            pasteCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }

    private fun buildNewPasteItem(
        newText: String,
        size: Long,
        hash: String,
        pasteItem: PasteItem,
    ): TextPasteItem =
        TextPasteItem(
            identifiers = pasteItem.identifiers,
            hash = hash,
            size = size,
            text = newText,
            extraInfo = pasteItem.extraInfo,
        )

    override suspend fun updateText(
        pasteData: PasteData,
        newText: String,
        pasteItem: PasteItem,
        pasteDao: PasteDao,
    ): Result<TextPasteItem> {
        val textBytes = newText.encodeToByteArray()
        val hash = codecsUtils.hash(textBytes)
        val newPasteItem = buildNewPasteItem(newText, textBytes.size.toLong(), hash, pasteItem)
        return pasteDao
            .updatePasteAppearItem(
                id = pasteData.id,
                pasteItem = newPasteItem,
                pasteSearchContent =
                    searchContentService.createSearchContent(
                        pasteData.source,
                        newPasteItem.getSearchContent(),
                    ),
                addedSize = newPasteItem.size - pasteItem.size,
            ).map {
                newPasteItem
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
