package com.crosspaste.clip.service

import com.crosspaste.app.AppInfo
import com.crosspaste.clip.ClipCollector
import com.crosspaste.clip.ClipItemService
import com.crosspaste.clip.item.TextClipItem
import com.crosspaste.dao.clip.ClipItem
import com.crosspaste.utils.getEncryptUtils
import io.realm.kotlin.MutableRealm
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class TextItemService(appInfo: AppInfo) : ClipItemService(appInfo) {

    companion object TextItemService {

        const val UNICODE_STRING = "Unicode String"
        const val TEXT = "text/plain"
        const val PLAIN_TEXT = "Plain Text"

        private val encryptUtils = getEncryptUtils()
    }

    override fun getIdentifiers(): List<String> {
        return listOf(UNICODE_STRING, TEXT, PLAIN_TEXT)
    }

    override fun createPreClipItem(
        clipId: Long,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
        clipCollector: ClipCollector,
    ) {
        TextClipItem().apply {
            this.identifier = identifier
        }.let {
            clipCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(
        transferData: Any,
        clipId: Long,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        clipCollector: ClipCollector,
    ) {
        if (transferData is String) {
            val textBytes = transferData.toByteArray()
            val md5 = encryptUtils.md5(textBytes)
            val update: (ClipItem, MutableRealm) -> Unit = { clipItem, realm ->
                realm.query(TextClipItem::class, "id == $0", clipItem.id).first().find()?.apply {
                    this.text = transferData
                    this.size = textBytes.size.toLong()
                    this.md5 = md5
                }
            }
            clipCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }
}
