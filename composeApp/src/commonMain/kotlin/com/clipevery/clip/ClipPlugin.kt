package com.clipevery.clip

import com.clipevery.dao.clip.ClipAppearItem
import io.realm.kotlin.MutableRealm

interface ClipPlugin {

    fun pluginProcess(clipAppearItems: List<ClipAppearItem>, realm: MutableRealm): List<ClipAppearItem>
}