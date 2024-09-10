package com.crosspaste.paste.plugin.type

import com.crosspaste.realm.paste.PasteItem
import io.realm.kotlin.MutableRealm

interface TextUpdater {
    fun updateText(
        newText: String,
        size: Long,
        hash: String,
        pasteItem: PasteItem,
        realm: MutableRealm,
    )
}
