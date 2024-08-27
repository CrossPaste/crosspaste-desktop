package com.crosspaste.paste.plugin.type

import com.crosspaste.dao.paste.PasteItem
import io.realm.kotlin.MutableRealm

interface TextUpdater {
    fun updateText(
        newText: String,
        size: Long,
        md5: String,
        pasteItem: PasteItem,
        realm: MutableRealm,
    )
}
