package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.isDirectory

class RemoveFolderImagePlugin(
    private val userDataPathProvider: UserDataPathProvider,
) : PasteProcessPlugin {
    override fun process(
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
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
                        imageItem.clear(
                            clearResource = true,
                            pasteCoordinate = pasteCoordinate,
                            userDataPathProvider = userDataPathProvider,
                        )
                        return pasteItems.filter { item -> item != imageItem }
                    }
                }
            }
        }
        return pasteItems
    }
}
