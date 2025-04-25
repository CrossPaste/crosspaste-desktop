package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getCodecsUtils

class MultiImagesPlugin(private val userDataPathProvider: UserDataPathProvider) : PasteProcessPlugin {

    private val codecsUtils = getCodecsUtils()

    override fun process(
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> {
        return if (pasteItems.size <= 1) {
            pasteItems
        } else {
            val allMemoryImages =
                pasteItems.map { it as ImagesPasteItem }.all {
                    it.identifiers.all { identifier -> identifier.startsWith("image") }
                }

            if (allMemoryImages) {
                val maxSizeImagePasteItem =
                    pasteItems.maxWith {
                            a, b ->
                        a.size.compareTo(b.size)
                    } as ImagesPasteItem

                pasteItems.filter { it != maxSizeImagePasteItem }
                    .forEach { it.clear(userDataPathProvider, clearResource = true) }

                listOf(maxSizeImagePasteItem)
            } else {
                val relativePathList =
                    pasteItems.map { it as ImagesPasteItem }.flatMap { it.relativePathList }
                val fileInfoMap =
                    pasteItems.map { it as ImagesPasteItem }
                        .flatMap { it.fileInfoTreeMap.entries }
                        .associate { it.key to it.value }
                val count = fileInfoMap.map { it.value.getCount() }.sum()
                val size = fileInfoMap.map { it.value.size }.sum()
                val hash =
                    pasteItems.map { it as ImagesPasteItem }.map { it.hash }
                        .toTypedArray().let { codecsUtils.hashByArray(it) }
                pasteItems.forEach { it.clear(userDataPathProvider, clearResource = false) }
                ImagesPasteItem(
                    identifiers = pasteItems.flatMap { it.identifiers },
                    count = count,
                    hash = hash,
                    size = size,
                    basePath = null,
                    relativePathList = relativePathList,
                    fileInfoTreeMap = fileInfoMap,
                ).let { listOf(it) }
            }
        }
    }
}
