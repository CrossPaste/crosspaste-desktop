package com.crosspaste.paste.plugin.type

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.PasteItem

class DesktopColorTypePlugin(
    private val searchContentService: SearchContentService,
) : ColorTypePlugin {

    override fun updateColor(
        pasteData: PasteData,
        newColor: Long,
        pasteItem: PasteItem,
        pasteDao: PasteDao,
    ) {
        val newPasteItem = (pasteItem as ColorPasteItem).update(newColor, newColor.toString())
        pasteDao.updatePasteAppearItem(
            id = pasteData.id,
            pasteItem = newPasteItem,
            pasteSearchContent =
                searchContentService.createSearchContent(
                    pasteData.source,
                    newPasteItem.getSearchContent(),
                ),
        )
    }

    override fun getPasteType(): PasteType = PasteType.COLOR_TYPE

    override fun getIdentifiers(): List<String> = listOf()

    override fun createPrePasteItem(
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
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
    }

    override fun buildTransferable(
        pasteItem: PasteItem,
        mixedCategory: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
    }
}
