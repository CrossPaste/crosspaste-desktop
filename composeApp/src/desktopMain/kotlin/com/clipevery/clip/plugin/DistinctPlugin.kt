package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.dao.clip.ClipItem
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.MutableRealm

object DistinctPlugin : ClipPlugin {

    private val childPlugins =
        mapOf(
            Pair(ClipType.IMAGE, MultiImagesPlugin),
            Pair(ClipType.FILE, MultFilesPlugin),
            Pair(ClipType.TEXT, FirstPlugin),
            Pair(ClipType.URL, FirstPlugin),
            Pair(ClipType.HTML, FirstPlugin),
        )

    override fun pluginProcess(
        clipItems: List<ClipItem>,
        realm: MutableRealm,
    ): List<ClipItem> {
        return clipItems.groupBy { it.getClipType() }.map { (clipType, items) ->
            val plugin = childPlugins[clipType]
            plugin?.pluginProcess(items, realm) ?: items
        }.flatten()
    }
}

object FirstPlugin : ClipPlugin {
    override fun pluginProcess(
        clipItems: List<ClipItem>,
        realm: MutableRealm,
    ): List<ClipItem> {
        return if (clipItems.isEmpty()) {
            listOf()
        } else {
            for (clipAppearItem in clipItems.drop(1)) {
                clipAppearItem.clear(realm)
            }
            listOf(clipItems.first())
        }
    }
}
