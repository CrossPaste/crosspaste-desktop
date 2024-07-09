package com.crosspaste.paste.service

import com.crosspaste.app.AppInfo
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteItemService
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.utils.getCodecsUtils
import io.realm.kotlin.MutableRealm
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class UrlItemService(appInfo: AppInfo) : PasteItemService(appInfo) {

    companion object UrlItemService {
        const val URL = "application/x-java-url"

        private val codecsUtils = getCodecsUtils()
    }

    override fun getIdentifiers(): List<String> {
        return listOf(URL)
    }

    override fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
        pasteCollector: PasteCollector,
    ) {
        UrlPasteItem().apply {
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
            val urlBytes = transferData.toByteArray()
            val md5 = codecsUtils.md5(urlBytes)
            val update: (PasteItem, MutableRealm) -> Unit = { pasteItem, realm ->
                realm.query(UrlPasteItem::class, "id == $0", pasteItem.id).first().find()?.apply {
                    this.url = transferData
                    this.size = urlBytes.size.toLong()
                    this.md5 = md5
                }
            }
            pasteCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }
}
