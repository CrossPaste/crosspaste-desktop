package com.crosspaste.paste.plugin.processs

import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.isDirectory
import io.realm.kotlin.MutableRealm

class RemoveFolderImagePlugin(
    private val userDataPathProvider: UserDataPathProvider,
) : PasteProcessPlugin {
    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
    ): List<PasteItem> {
        pasteItems.firstOrNull { it.getPasteType() == PasteType.IMAGE }?.let { imageItem ->
            val files = imageItem as PasteFiles
            if (files.getFilePaths(userDataPathProvider).size == 1) {
                pasteItems.firstOrNull { it.getPasteType() == PasteType.FILE }?.let {
                    val pasteFiles = it as PasteFiles
                    if (it.getFilePaths(userDataPathProvider).size == 1 &&
                        pasteFiles.getFilePaths(userDataPathProvider)[0].isDirectory
                    ) {
                        imageItem.clear(realm, userDataPathProvider, clearResource = true)
                        return pasteItems.filter { item -> item != imageItem }
                    }
                }
            }
        }
        return pasteItems
    }
}
