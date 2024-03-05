package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.MutableRealm

object DistinctPlugin: ClipPlugin {

    private val childPlugins = mapOf(
        Pair(ClipType.IMAGE, MultiImagesPlugin),
        Pair(ClipType.FILE, MultFilesPlugin),
        Pair(ClipType.TEXT, FirstPlugin),
        Pair(ClipType.URL, FirstPlugin),
        Pair(ClipType.HTML, FirstPlugin)
    )

    override fun pluginProcess(clipAppearItems: List<ClipAppearItem>, realm: MutableRealm): List<ClipAppearItem> {
        return clipAppearItems.map { clipAppearItem ->
            val plugin = childPlugins[clipAppearItem.getClipType()]
            plugin?.pluginProcess(listOf(clipAppearItem), realm) ?: listOf(clipAppearItem)
        }.flatten()
    }
}

object FirstPlugin: ClipPlugin {
    override fun pluginProcess(clipAppearItems: List<ClipAppearItem>, realm: MutableRealm): List<ClipAppearItem> {
        return if (clipAppearItems.isEmpty()) {
            listOf()
        } else {
            for (clipAppearItem in clipAppearItems.drop(0)) {
                clipAppearItem.clear(realm)
            }
            listOf(clipAppearItems.first())
        }
    }

}