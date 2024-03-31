package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.MutableRealm

object SortPlugin: ClipPlugin {

    private val itemPriorityMap: Map<Int, Int> = mapOf(
        ClipType.IMAGE to 4,
        ClipType.FILE to 3,
        ClipType.HTML to 2,
        ClipType.URL to 1,
        ClipType.TEXT to 0,
        ClipType.INVALID to -1
    )

    override fun pluginProcess(clipAppearItems: List<ClipAppearItem>, realm: MutableRealm): List<ClipAppearItem> {
        return clipAppearItems.sortedByDescending { itemPriorityMap[it.getClipType()]!! }
    }
}