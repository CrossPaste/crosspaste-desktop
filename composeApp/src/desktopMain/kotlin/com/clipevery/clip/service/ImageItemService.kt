package com.clipevery.clip.service

import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class ImageItemService: ClipItemService {
    override fun getIdentifiers(): List<String> {
        return listOf()
    }

    override fun doCreateClipItem(
        transferData: Any,
        clipId: Int,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        TODO("Not yet implemented")
    }

}