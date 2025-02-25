package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getCodecsUtils

class MultiFilesPlugin(private val userDataPathProvider: UserDataPathProvider) : PasteProcessPlugin {

    private val codecsUtils = getCodecsUtils()

    override fun process(
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> {
        if (pasteItems.size <= 1) {
            return pasteItems
        } else {
            val relativePathList =
                pasteItems.map { it as FilesPasteItem }.flatMap { it.relativePathList }
            val fileInfoMap =
                pasteItems.map { it as FilesPasteItem }
                    .flatMap { it.fileInfoTreeMap.entries }
                    .associate { it.key to it.value }
            val hash =
                pasteItems.map { it as FilesPasteItem }.map { it.hash }
                    .toTypedArray().let { codecsUtils.hashByArray(it) }
            pasteItems.forEach { it.clear(userDataPathProvider, clearResource = false) }
            return FilesPasteItem(
                identifiers = pasteItems.flatMap { it.identifiers },
                count = fileInfoMap.values.sumOf { it.getCount() },
                hash = hash,
                size = pasteItems.sumOf { it.size },
                basePath = null,
                relativePathList = relativePathList,
                fileInfoTreeMap = fileInfoMap,
            ).let { listOf(it) }
        }
    }
}
