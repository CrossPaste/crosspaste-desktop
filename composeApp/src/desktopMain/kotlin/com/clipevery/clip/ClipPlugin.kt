package com.clipevery.clip

import com.clipevery.dao.clip.ClipAppearItem
import kotlin.reflect.KClass

interface SingleClipPlugin {

    fun pluginProcess(clipAppearItems: Map<KClass<out ClipItemService>, ClipAppearItem>): Map<KClass<out ClipItemService>, ClipAppearItem>

}

interface MultiClipPlugin {

    fun pluginProcess(clipAppearItems: List<ClipAppearItem>): List<ClipAppearItem>

}