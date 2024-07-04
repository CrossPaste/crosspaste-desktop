package com.crosspaste.paste.service

import com.crosspaste.app.AppInfo
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteItemService
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.utils.getEncryptUtils
import io.realm.kotlin.MutableRealm
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class TextItemService(appInfo: AppInfo) : PasteItemService(appInfo) {

    companion object TextItemService {

        const val UNICODE_STRING = "Unicode String"
        const val TEXT = "text/plain"
        const val PLAIN_TEXT = "Plain Text"

        private val encryptUtils = getEncryptUtils()
    }

    override fun getIdentifiers(): List<String> {
        return listOf(UNICODE_STRING, TEXT, PLAIN_TEXT)
    }

    override fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
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
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        pasteCollector: PasteCollector,
    ) {
        if (transferData is String) {
            val textBytes = transferData.toByteArray()
            val md5 = encryptUtils.md5(textBytes)
            val update: (PasteItem, MutableRealm) -> Unit = { pasteItem, realm ->
                realm.query(TextPasteItem::class, "id == $0", pasteItem.id).first().find()?.apply {
                    this.text = transferData
                    this.size = textBytes.size.toLong()
                    this.md5 = md5
                }
            }
            pasteCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }
}
