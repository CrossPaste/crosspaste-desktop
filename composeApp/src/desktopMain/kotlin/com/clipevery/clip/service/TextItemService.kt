package com.clipevery.clip.service

import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import java.awt.datatransfer.Transferable

class TextItemService: ClipItemService {
    override fun getIdentifiers(): List<String> {
        TODO("Not yet implemented")
    }

    override fun createClipItem(
        clipId: Int,
        itemIndex: Int,
        hpn: String?,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        TODO("Not yet implemented")
    }
}