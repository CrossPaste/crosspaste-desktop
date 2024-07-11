package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteItem
import io.realm.kotlin.MutableRealm

interface PasteProcessPlugin {

    fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem>
}
