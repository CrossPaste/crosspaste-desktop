package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.clip.item.TextClipItem
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.dao.clip.ClipAppearItem

class UrlTextCombinePlugin: ClipPlugin {

    override fun pluginProcess(clipAppearItems: List<ClipAppearItem>): List<ClipAppearItem> {
        clipAppearItems.filterIsInstance<UrlClipItem>().firstOrNull()?.let { urlClipItem ->
            val remainingItems = clipAppearItems.filterNot {
                it is UrlClipItem || (it is TextClipItem && it.text == urlClipItem.url)
            }
            return listOf(urlClipItem) + remainingItems
        } ?: return clipAppearItems
    }
}