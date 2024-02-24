package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType

object ImageHtmlCombinePlugin: ClipPlugin {
    override fun pluginProcess(clipAppearItems: List<ClipAppearItem>): List<ClipAppearItem> {
        if (clipAppearItems.size == 2) {
            val imageItem = clipAppearItems.firstOrNull { it.getClipType() == ClipType.IMAGE }
            val htmlItem = clipAppearItems.firstOrNull { it.getClipType() == ClipType.HTML }
            if (imageItem != null && htmlItem != null) {
                return listOf(imageItem)
            }
        }
        return clipAppearItems
    }
}