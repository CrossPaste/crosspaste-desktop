package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.PasteItem
import io.realm.kotlin.MutableRealm

interface PasteProcessPlugin {

    fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
        source: String?,
    ): List<PasteItem>
}
