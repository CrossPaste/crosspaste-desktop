package com.crosspaste.clip.plugin

import com.crosspaste.clip.ClipPlugin
import com.crosspaste.clip.item.ClipFiles
import com.crosspaste.dao.clip.ClipItem
import com.crosspaste.dao.clip.ClipType
import io.realm.kotlin.MutableRealm
import kotlin.io.path.isDirectory

object RemoveFolderImagePlugin : ClipPlugin {
    override fun pluginProcess(
        clipItems: List<ClipItem>,
        realm: MutableRealm,
    ): List<ClipItem> {
        clipItems.firstOrNull { it.getClipType() == ClipType.IMAGE }?.let { imageItem ->
            val files = imageItem as ClipFiles
            if (files.getFilePaths().size == 1) {
                clipItems.firstOrNull { it.getClipType() == ClipType.FILE }?.let {
                    val clipFiles = it as ClipFiles
                    if (it.getFilePaths().size == 1 && clipFiles.getFilePaths()[0].isDirectory()) {
                        imageItem.clear(realm, clearResource = true)
                        return clipItems.filter { item -> item != imageItem }
                    }
                }
            }
        }
        return clipItems
    }
}
