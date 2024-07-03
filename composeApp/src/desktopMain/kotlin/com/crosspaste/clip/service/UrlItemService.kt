package com.crosspaste.clip.service

import com.crosspaste.app.AppInfo
import com.crosspaste.clip.ClipCollector
import com.crosspaste.clip.ClipItemService
import com.crosspaste.clip.item.UrlClipItem
import com.crosspaste.dao.clip.ClipItem
import com.crosspaste.utils.getEncryptUtils
import io.realm.kotlin.MutableRealm
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class UrlItemService(appInfo: AppInfo) : ClipItemService(appInfo) {

    companion object UrlItemService {
        const val URL = "application/x-java-url"

        private val encryptUtils = getEncryptUtils()
    }

    override fun getIdentifiers(): List<String> {
        return listOf(URL)
    }

    override fun createPreClipItem(
        clipId: Long,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
        clipCollector: ClipCollector,
    ) {
        UrlClipItem().apply {
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
            val urlBytes = transferData.toByteArray()
            val md5 = encryptUtils.md5(urlBytes)
            val update: (ClipItem, MutableRealm) -> Unit = { clipItem, realm ->
                realm.query(UrlClipItem::class, "id == $0", clipItem.id).first().find()?.apply {
                    this.url = transferData
                    this.size = urlBytes.size.toLong()
                    this.md5 = md5
                }
            }
            clipCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }
}
