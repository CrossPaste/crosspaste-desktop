package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.isDirectory
import io.realm.kotlin.MutableRealm

class RemoveFolderImagePlugin(
    private val userDataPathProvider: UserDataPathProvider,
) : PasteProcessPlugin {
    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
        source: String?,
    ): List<PasteItem> {
        pasteItems.firstOrNull { it.getPasteType().isImage() }?.let { imageItem ->
            val files = imageItem as PasteFiles
            if (files.getFilePaths(userDataPathProvider).size == 1) {
                pasteItems.firstOrNull { it.getPasteType().isFile() }?.let {
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
