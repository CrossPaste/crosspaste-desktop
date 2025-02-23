package com.crosspaste.paste.plugin.process

import com.crosspaste.app.AppFileType
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.extension
import com.crosspaste.utils.getFileUtils

class FilesToImagesPlugin(
    private val userDataPathProvider: UserDataPathProvider,
) : PasteProcessPlugin {

    private val fileUtils = getFileUtils()

    private val fileBasePath = userDataPathProvider.resolve(appFileType = AppFileType.FILE)
    private val imageBasePath = userDataPathProvider.resolve(appFileType = AppFileType.IMAGE)

    override fun process(
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> {
        return pasteItems.map { pasteAppearItem ->
            if (pasteAppearItem is FilesPasteItem) {
                if (pasteAppearItem.getFilePaths(userDataPathProvider)
                        .map { path -> path.extension }
                        .all { fileUtils.canPreviewImage(it) }
                ) {
                    val basePath = pasteAppearItem.basePath
                    if (basePath == null) {
                        pasteAppearItem.relativePathList.map {
                            val srcPath =
                                userDataPathProvider.resolve(
                                    fileBasePath,
                                    it,
                                    autoCreate = false,
                                    isFile = true,
                                )
                            val destPath =
                                userDataPathProvider.resolve(
                                    imageBasePath,
                                    it,
                                    autoCreate = true,
                                    isFile = true,
                                )
                            if (fileUtils.moveFile(srcPath, destPath).isFailure) {
                                throw IllegalStateException("Failed to move file from $srcPath to $destPath")
                            }
                        }
                    }
                    val identifiers = pasteAppearItem.identifiers
                    val relativePathList = pasteAppearItem.relativePathList
                    val fileInfoTreeMap = pasteAppearItem.fileInfoTreeMap
                    val count = pasteAppearItem.count
                    val size = pasteAppearItem.size
                    val hash = pasteAppearItem.hash
                    pasteAppearItem.clear(userDataPathProvider, clearResource = false)
                    ImagesPasteItem(
                        identifiers = identifiers,
                        count = count,
                        hash = hash,
                        size = size,
                        basePath = imageBasePath.toString(),
                        relativePathList = relativePathList,
                        fileInfoTreeMap = fileInfoTreeMap,
                    )
                } else {
                    pasteAppearItem
                }
            } else {
                pasteAppearItem
            }
        }
    }
}
