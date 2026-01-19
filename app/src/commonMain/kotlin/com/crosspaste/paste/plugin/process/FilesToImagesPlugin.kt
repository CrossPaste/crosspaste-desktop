package com.crosspaste.paste.plugin.process

import com.crosspaste.app.AppFileType
import com.crosspaste.paste.item.CreatePasteItemHelper.createImagesPasteItem
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
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
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> =
        pasteItems.map { pasteAppearItem ->
            if (pasteAppearItem is FilesPasteItem) {
                if (pasteAppearItem
                        .getFilePaths(userDataPathProvider)
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
                    pasteAppearItem.clear(
                        clearResource = false,
                        pasteCoordinate = pasteCoordinate,
                        userDataPathProvider = userDataPathProvider,
                    )
                    createImagesPasteItem(
                        identifiers = identifiers,
                        basePath = basePath,
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
