package com.clipevery.clip

import java.awt.datatransfer.Transferable

interface ClipItemService {

    fun getIdentifiers(): List<String>

    fun createClipItem(
        clipId: Int,
        itemIndex: Int,
        hpn: String?,
        transferable: Transferable,
        clipCollector: ClipCollector
    )

//    fun createPreClipItem(clipId: Int, itemIndex: Int, identifier: String, itemProvider: DataFlavor, clipCollector: ClipCollector)
}