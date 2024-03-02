package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.clip.item.TextClipItem
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.dao.clip.ClipAppearItem
import io.realm.kotlin.MutableRealm

object UrlTextCombinePlugin: ClipPlugin {

    override fun pluginProcess(clipAppearItems: List<ClipAppearItem>, realm: MutableRealm): List<ClipAppearItem> {
        // todo recalculate the logic
        clipAppearItems.filterIsInstance<UrlClipItem>().firstOrNull()?.let { urlClipItem ->
            val remainingItems = clipAppearItems.filterNot {
                it is UrlClipItem || (it is TextClipItem && it.text == urlClipItem.url)
            }
            return listOf(urlClipItem) + remainingItems
        } ?: return clipAppearItems
    }
}