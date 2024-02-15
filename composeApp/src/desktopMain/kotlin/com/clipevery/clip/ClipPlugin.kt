package com.clipevery.clip

import com.clipevery.dao.clip.ClipAppearItem
import kotlin.reflect.KClass

interface SingleClipPlugin {

    fun pluginProcess(clipAppearItems: Map<KClass<ClipItemService>, ClipAppearItem>): Map<KClass<ClipItemService>, ClipAppearItem>

}

interface MultiClipPlugin {

    fun pluginProcess(clipAppearItems: List<ClipAppearItem>): List<ClipAppearItem>

}