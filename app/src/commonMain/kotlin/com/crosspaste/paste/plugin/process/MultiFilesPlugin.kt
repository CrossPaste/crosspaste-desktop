package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider

class MultiFilesPlugin(
    private val userDataPathProvider: UserDataPathProvider,
) : PasteProcessPlugin {

    override fun process(
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> =
        if (pasteItems.size <= 1) {
            pasteItems
        } else {
            val relativePathList =
                pasteItems.map { it as FilesPasteItem }.flatMap { it.relativePathList }
            val fileInfoMap =
                pasteItems
                    .map { it as FilesPasteItem }
                    .flatMap { it.fileInfoTreeMap.entries }
                    .associate { it.key to it.value }
            pasteItems.forEach {
                it.clear(
                    clearResource = false,
                    pasteCoordinate = pasteCoordinate,
                    userDataPathProvider = userDataPathProvider,
                )
            }
            createFilesPasteItem(
                identifiers = pasteItems.flatMap { it.identifiers },
                basePath = null,
                relativePathList = relativePathList,
                fileInfoTreeMap = fileInfoMap,
            ).let { listOf(it) }
        }
}
