package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.dao.clip.ClipAppearItem

object SortPlugin: ClipPlugin {
    override fun pluginProcess(clipAppearItems: List<ClipAppearItem>): List<ClipAppearItem> {
        return clipAppearItems.sortedByDescending { it.getClipType() }
    }
}