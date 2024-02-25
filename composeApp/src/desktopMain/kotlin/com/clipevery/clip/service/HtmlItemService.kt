package com.clipevery.clip.service

import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import com.clipevery.clip.item.HtmlClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.utils.md5ByString
import io.realm.kotlin.MutableRealm
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class HtmlItemService: ClipItemService {

    companion object HtmlItemService {

        const val HTML_ID = "text/html"
    }

    override fun getIdentifiers(): List<String> {
        return listOf(HTML_ID)
    }

    override fun createPreClipItem(
        clipId: Int,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        HtmlClipItem().apply {
            this.identifier = identifier
        }.let {
            clipCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(transferData: Any,
                                      clipId: Int,
                                      itemIndex: Int,
                                      dataFlavor: DataFlavor,
                                      dataFlavorMap: Map<String, List<DataFlavor>>,
                                      transferable: Transferable,
                                      clipCollector: ClipCollector) {
        if (transferData is String) {
            val md5 = md5ByString(transferData)
            val update: (ClipAppearItem, MutableRealm) -> Unit = { clipItem, realm ->
                realm.query(HtmlClipItem::class).query("id == $0", clipItem.id).first().find()?.apply {
                    this.html = transferData
                    this.md5 = md5
                }
            }
            clipCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }
}