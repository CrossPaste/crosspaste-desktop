package com.clipevery.clip.service

import com.clipevery.app.AppInfo
import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.utils.getEncryptUtils
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
            val update: (ClipAppearItem, MutableRealm) -> Unit = { clipItem, realm ->
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
