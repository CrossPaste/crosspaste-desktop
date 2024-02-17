package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.clip.item.ImageClipItem
import com.clipevery.clip.item.ImagesClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import com.clipevery.utils.md5ByArray
import io.realm.kotlin.ext.toRealmList

class MultiImagePlugin: ClipPlugin {
    override fun pluginProcess(clipAppearItems: List<ClipAppearItem>): List<ClipAppearItem> {
        val clipTypes: Set<Int> = clipAppearItems.map { clipAppearItem ->
            clipAppearItem.getClipType()
        }.toSet()
        if (clipAppearItems.size > 1 && clipTypes.size == 1 && clipAppearItems[0].getClipType() == ClipType.IMAGE) {
            val imageClipItems = clipAppearItems.map { it as ImageClipItem }.toRealmList()
            val md5 = md5ByArray(imageClipItems.map { it.md5 }.toTypedArray())
            return ImagesClipItem().apply {
                this.imageClipItems = imageClipItems
                this.md5 = md5
            }.let { listOf(it) }
        }
        return clipAppearItems
    }
}