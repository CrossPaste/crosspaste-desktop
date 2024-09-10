package com.crosspaste.paste.plugin.type

import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.utils.getCodecsUtils
import io.realm.kotlin.MutableRealm
import java.awt.datatransfer.DataFlavor

class TextTypePlugin : PasteTypePlugin, TextUpdater {

    companion object TextItemService {

        const val UNICODE_STRING = "Unicode String"
        const val TEXT = "text/plain"
        const val PLAIN_TEXT = "Plain Text"

        private val codecsUtils = getCodecsUtils()
    }

    override fun getPasteType(): Int {
        return PasteType.TEXT
    }

    override fun getIdentifiers(): List<String> {
        return listOf(UNICODE_STRING, TEXT, PLAIN_TEXT)
    }

    override fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        TextPasteItem().apply {
            this.identifier = identifier
        }.let {
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
            val textBytes = transferData.toByteArray()
            val hash = codecsUtils.hash(textBytes)
            val update: (PasteItem, MutableRealm) -> Unit = { pasteItem, realm ->
                updateText(transferData, textBytes.size.toLong(), hash, pasteItem, realm)
            }
            pasteCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }

    override fun updateText(
        newText: String,
        size: Long,
        hash: String,
        pasteItem: PasteItem,
        realm: MutableRealm,
    ) {
        realm.query(TextPasteItem::class, "id == $0", pasteItem.id).first().find()?.apply {
            this.text = newText
            this.size = size
            this.hash = hash
        }
    }

    override fun buildTransferable(
        pasteItem: PasteItem,
        singleType: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
        pasteItem as TextPasteItem
        map[DataFlavor.stringFlavor.toPasteDataFlavor()] = pasteItem.text
    }
}
