package com.clipevery.clip.plugin.single

import com.clipevery.clip.ClipItemService
import com.clipevery.clip.SingleClipPlugin
import com.clipevery.dao.clip.ClipAppearItem
import kotlin.reflect.KClass

class UrlTextCombinePlugin: SingleClipPlugin {
    override fun pluginProcess(clipAppearItems: Map<KClass<ClipItemService>, ClipAppearItem>): Map<KClass<ClipItemService>, ClipAppearItem> {
        TODO("Not yet implemented")
    }
}