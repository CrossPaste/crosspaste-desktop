package com.crosspaste.paste.plugin.type

import com.crosspaste.realm.paste.PasteItem
import io.realm.kotlin.MutableRealm

interface ColorTypePlugin : PasteTypePlugin {

    fun updateColor(
        newColor: Long,
        pasteItem: PasteItem,
        realm: MutableRealm,
    )
}
