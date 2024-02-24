package com.clipevery.clip.service

import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import com.clipevery.clip.item.HtmlClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.utils.md5ByString
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class HtmlItemService: ClipItemService {

    companion object HtmlItemService {

        const val HTML_ID = "text/html"
    }

    override fun getIdentifiers(): List<String> {
        return listOf(HTML_ID)
    }

    override fun doCreateClipItem(transferData: Any,
                                  clipId: Int,
                                  itemIndex: Int,
                                  dataFlavor: DataFlavor,
                                  dataFlavorMap: Map<String, List<DataFlavor>>,
                                  transferable: Transferable,
                                  clipCollector: ClipCollector) {
        var clipItem: ClipAppearItem? = null
        if (transferData is String) {
            clipItem = HtmlClipItem().apply {
                identifier = dataFlavor.humanPresentableName
                html = transferData
                md5 = md5ByString(html)
            }
        }
        clipItem?.let { clipCollector.collectItem(itemIndex, this::class, it) }
    }


}