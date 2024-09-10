package com.crosspaste.paste.plugin.process

import com.crosspaste.realm.paste.PasteItem
import io.realm.kotlin.MutableRealm

interface PasteProcessPlugin {

    fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem>
}
