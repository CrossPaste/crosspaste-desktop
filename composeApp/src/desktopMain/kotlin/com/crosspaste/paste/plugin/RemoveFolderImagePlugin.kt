package com.crosspaste.paste.plugin

import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.paste.PasteProcessPlugin
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.utils.isDirectory
import io.realm.kotlin.MutableRealm

object RemoveFolderImagePlugin : PasteProcessPlugin {
    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem> {
        pasteItems.firstOrNull { it.getPasteType() == PasteType.IMAGE }?.let { imageItem ->
            val files = imageItem as PasteFiles
            if (files.getFilePaths().size == 1) {
                pasteItems.firstOrNull { it.getPasteType() == PasteType.FILE }?.let {
                    val pasteFiles = it as PasteFiles
                    if (it.getFilePaths().size == 1 && pasteFiles.getFilePaths()[0].isDirectory) {
                        imageItem.clear(realm, clearResource = true)
                        return pasteItems.filter { item -> item != imageItem }
                    }
                }
            }
        }
        return pasteItems
    }
}
