package com.clipevery.clip.service

import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.utils.md5ByString
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.types.RealmObject
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class UrlItemService: ClipItemService {

    companion object UrlItemService {
        const val URL = "application/x-java-url"
    }

    override fun getIdentifiers(): List<String> {
        return listOf(URL)
    }

    override fun createPreClipItem(
        clipId: Int,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        UrlClipItem().apply {
            this.identifier = identifier
        }.let {
            clipCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(
        transferData: Any,
        clipId: Int,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        if (transferData is String) {
            val md5 = md5ByString(transferData)
            val update: (ClipAppearItem, MutableRealm) -> Unit = { clipItem, realm ->
                (realm.findLatest(clipItem as RealmObject) as UrlClipItem).apply {
                    this.url = transferData
                    this.md5 = md5
                }
            }
            clipCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }
}