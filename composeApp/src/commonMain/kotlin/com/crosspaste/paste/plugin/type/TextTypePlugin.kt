package com.crosspaste.paste.plugin.type

import com.crosspaste.paste.item.PasteItem
import io.realm.kotlin.MutableRealm

interface TextTypePlugin : PasteTypePlugin {

    fun updateText(
        newText: String,
        size: Long,
        hash: String,
        pasteItem: PasteItem,
        realm: MutableRealm,
    )
}
