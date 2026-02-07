package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.CreatePasteItemHelper.createImagesPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider

class MultiImagesPlugin(
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
            val allMemoryImages =
                pasteItems.map { it as ImagesPasteItem }.all {
                    it.identifiers.all { identifier -> identifier.startsWith("image") }
                }

            if (allMemoryImages) {
                val maxSizeImagePasteItem =
                    pasteItems.maxBy { it.size } as ImagesPasteItem

                pasteItems
                    .filter { it != maxSizeImagePasteItem }
                    .forEach {
                        it.clear(
                            clearResource = true,
                            pasteCoordinate = pasteCoordinate,
                            userDataPathProvider = userDataPathProvider,
                        )
                    }

                listOf(maxSizeImagePasteItem)
            } else {
                val relativePathList =
                    pasteItems.map { it as ImagesPasteItem }.flatMap { it.relativePathList }
                val fileInfoMap =
                    pasteItems
                        .map { it as ImagesPasteItem }
                        .flatMap { it.fileInfoTreeMap.entries }
                        .associate { it.key to it.value }
                pasteItems.forEach {
                    it.clear(
                        clearResource = false,
                        pasteCoordinate = pasteCoordinate,
                        userDataPathProvider = userDataPathProvider,
                    )
                }
                createImagesPasteItem(
                    identifiers = pasteItems.flatMap { it.identifiers },
                    relativePathList = relativePathList,
                    fileInfoTreeMap = fileInfoMap,
                ).let { listOf(it) }
            }
        }
}
