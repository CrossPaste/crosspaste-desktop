package com.crosspaste.clip

import com.crosspaste.dao.clip.ClipItem
import io.realm.kotlin.MutableRealm

interface ClipPlugin {

    fun pluginProcess(
        clipItems: List<ClipItem>,
        realm: MutableRealm,
    ): List<ClipItem>
}
