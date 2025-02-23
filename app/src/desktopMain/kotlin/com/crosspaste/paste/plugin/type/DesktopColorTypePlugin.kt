package com.crosspaste.paste.plugin.type

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.PasteItem

class DesktopColorTypePlugin : ColorTypePlugin {

    override fun updateColor(
        id: Long,
        newColor: Long,
        pasteItem: PasteItem,
        pasteDao: PasteDao,
    ) {
        pasteDao.updatePasteAppearItem(
            id,
            (pasteItem as ColorPasteItem).update(newColor, newColor.toString()),
        )
    }

    override fun getPasteType(): PasteType {
        return PasteType.COLOR_TYPE
    }

    override fun getIdentifiers(): List<String> {
        return listOf()
    }

    override fun createPrePasteItem(
        pasteId: Long,
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
        singleType: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
    }
}
