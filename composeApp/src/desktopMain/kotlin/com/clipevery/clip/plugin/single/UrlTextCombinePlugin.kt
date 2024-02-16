package com.clipevery.clip.plugin.single

import com.clipevery.clip.ClipItemService
import com.clipevery.clip.SingleClipPlugin
import com.clipevery.clip.service.TextItemService
import com.clipevery.clip.service.UrlItemService
import com.clipevery.dao.clip.ClipAppearItem
import kotlin.reflect.KClass

class UrlTextCombinePlugin: SingleClipPlugin {
    override fun pluginProcess(clipAppearItems: Map<KClass<out ClipItemService>, ClipAppearItem>): Map<KClass<out ClipItemService>, ClipAppearItem> {
        clipAppearItems[UrlItemService::class] ?: return clipAppearItems
        clipAppearItems[TextItemService::class] ?: return clipAppearItems
        val modifiedAppearCollector = clipAppearItems.toMutableMap()
        modifiedAppearCollector.remove(TextItemService::class)
        return modifiedAppearCollector
    }
}