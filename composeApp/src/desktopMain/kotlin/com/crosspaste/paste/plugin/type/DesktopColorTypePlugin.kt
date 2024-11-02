package com.crosspaste.paste.plugin.type

import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteType
import io.realm.kotlin.MutableRealm

class DesktopColorTypePlugin : ColorTypePlugin {

    override fun updateColor(
        newColor: Long,
        pasteItem: PasteItem,
        realm: MutableRealm,
    ) {
        realm.query(ColorPasteItem::class, "id == $0", pasteItem.id).first().find()?.apply {
            this.color = newColor
            this.hash = newColor.toString()
        }
    }

    override fun getPasteType(): Int {
        return PasteType.COLOR
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
