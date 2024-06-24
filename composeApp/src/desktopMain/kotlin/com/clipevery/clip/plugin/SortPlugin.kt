package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.dao.clip.ClipItem
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.MutableRealm

object SortPlugin : ClipPlugin {

    private val itemPriorityMap: Map<Int, Int> =
        mapOf(
            ClipType.FILE to 4,
            ClipType.IMAGE to 3,
            ClipType.HTML to 2,
            ClipType.URL to 1,
            ClipType.TEXT to 0,
            ClipType.INVALID to -1,
        )

    override fun pluginProcess(
        clipItems: List<ClipItem>,
        realm: MutableRealm,
    ): List<ClipItem> {
        return clipItems.sortedByDescending { itemPriorityMap[it.getClipType()]!! }
    }
}
