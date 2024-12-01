package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.PasteItem
import io.realm.kotlin.MutableRealm

object SortPlugin : PasteProcessPlugin {

    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
        source: String?,
    ): List<PasteItem> {
        return pasteItems.sortedByDescending { it.getPasteType().priority }
    }
}
