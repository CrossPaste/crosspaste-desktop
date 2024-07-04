package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteItem
import io.realm.kotlin.MutableRealm

interface PastePlugin {

    fun pluginProcess(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem>
}
