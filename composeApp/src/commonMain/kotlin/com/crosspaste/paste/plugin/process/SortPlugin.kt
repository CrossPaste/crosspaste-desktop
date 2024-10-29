package com.crosspaste.paste.plugin.process

import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteType
import io.realm.kotlin.MutableRealm

object SortPlugin : PasteProcessPlugin {

    private val itemPriorityMap: Map<Int, Int> =
        mapOf(
            PasteType.FILE to 5,
            PasteType.HTML to 4,
            PasteType.RTF to 3,
            PasteType.IMAGE to 2,
            PasteType.URL to 1,
            PasteType.TEXT to 0,
            PasteType.INVALID to -1,
        )

    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
        source: String?,
    ): List<PasteItem> {
        return pasteItems.sortedByDescending { itemPriorityMap[it.getPasteType()]!! }
    }
}
