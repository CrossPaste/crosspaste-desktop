package com.crosspaste.paste.plugin.processs

import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import io.realm.kotlin.MutableRealm

object SortPlugin : PasteProcessPlugin {

    private val itemPriorityMap: Map<Int, Int> =
        mapOf(
            PasteType.FILE to 4,
            PasteType.IMAGE to 3,
            PasteType.HTML to 2,
            PasteType.URL to 1,
            PasteType.TEXT to 0,
            PasteType.INVALID to -1,
        )

    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem> {
        return pasteItems.sortedByDescending { itemPriorityMap[it.getPasteType()]!! }
    }
}
