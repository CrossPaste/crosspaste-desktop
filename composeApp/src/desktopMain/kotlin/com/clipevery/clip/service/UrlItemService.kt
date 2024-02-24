package com.clipevery.clip.service

import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.utils.md5ByString
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class UrlItemService: ClipItemService {

    companion object UrlItemService {
        const val URL = "application/x-java-url"
    }

    override fun getIdentifiers(): List<String> {
        return listOf(URL)
    }

    override fun doCreateClipItem(
        transferData: Any,
        clipId: Int,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        var clipItem: ClipAppearItem? = null
        if (transferData is String) {
            clipItem = UrlClipItem().apply {
                identifier = dataFlavor.humanPresentableName
                url = transferData
                md5 = md5ByString(url)
            }
        }
        clipItem?.let {
            clipCollector.collectItem(itemIndex, this::class, it)
        }
    }
}