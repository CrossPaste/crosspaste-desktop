package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.MutableRealm
import kotlin.io.path.isDirectory

object RemoveFolderImagePlugin : ClipPlugin {
    override fun pluginProcess(
        clipAppearItems: List<ClipAppearItem>,
        realm: MutableRealm,
    ): List<ClipAppearItem> {
        clipAppearItems.firstOrNull { it.getClipType() == ClipType.IMAGE }?.let { imageItem ->
            val files = imageItem as ClipFiles
            if (files.getFilePaths().size == 1) {
                clipAppearItems.firstOrNull { it.getClipType() == ClipType.FILE }?.let {
                    val clipFiles = it as ClipFiles
                    if (it.getFilePaths().size == 1 && clipFiles.getFilePaths()[0].isDirectory()) {
                        imageItem.clear(realm, clearResource = true)
                        return clipAppearItems.filter { item -> item != imageItem }
                    }
                }
            }
        }
        return clipAppearItems
    }
}
