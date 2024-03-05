package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.clip.item.ImagesClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import com.clipevery.utils.md5ByArray
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList

object MultiImagePlugin: ClipPlugin {
    override fun pluginProcess(clipAppearItems: List<ClipAppearItem>, realm: MutableRealm): List<ClipAppearItem> {
        val clipTypes: Set<Int> = clipAppearItems.map { clipAppearItem ->
            clipAppearItem.getClipType()
        }.toSet()
        if (clipAppearItems.size > 1 && clipTypes.size == 1 && clipAppearItems[0].getClipType() == ClipType.IMAGE) {
            val relativePathList = clipAppearItems.map { it as ImagesClipItem }.flatMap { it.relativePathList }
                .toRealmList()

            val md5List = clipAppearItems.map { it as ImagesClipItem }.flatMap { it.md5List }
                .toRealmList()

            val md5 = md5ByArray(md5List.map { it }.toTypedArray())

            clipAppearItems.forEach { it.clear(realm, clearResource = false) }
            return ImagesClipItem().apply {
                this.relativePathList = relativePathList
                this.md5List = md5List
                this.md5 = md5
            }.let { listOf(it) }
        }
        return clipAppearItems
    }
}