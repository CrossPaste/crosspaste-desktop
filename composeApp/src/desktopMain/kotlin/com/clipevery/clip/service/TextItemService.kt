package com.clipevery.clip.service

import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import com.clipevery.clip.item.TextClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.utils.md5ByString
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class TextItemService: ClipItemService {

    companion object TextItemService {

        const val UNICODE_STRING = "Unicode String"
        const val TEXT = "text/plain"
        const val PLAIN_TEXT = "Plain Text"
    }

    override fun getIdentifiers(): List<String> {
        return listOf(UNICODE_STRING, TEXT, PLAIN_TEXT)
    }

    override fun doCreateClipItem(
        transferData: Any,
        clipId: Int,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        var clipItem: ClipAppearItem? = null
        if (transferData is String) {
            clipItem = TextClipItem().apply {
                identifier = dataFlavor.humanPresentableName
                text = transferData
                md5 = md5ByString(text)
            }
        }
        clipItem?.let { clipCollector.collectItem(itemIndex, this::class, it) }
    }
}