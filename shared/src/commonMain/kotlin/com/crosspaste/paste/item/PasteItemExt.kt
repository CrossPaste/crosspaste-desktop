package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import okio.Path
import okio.Path.Companion.toPath

private val fileUtils = getFileUtils()

fun UrlPasteItem.getRenderingFilePath(
    pasteCoordinate: PasteCoordinate,
    userDataPathProvider: UserDataPathProvider,
): Path =
    getMarketingPath()?.toPath() ?: run {
        val basePath = userDataPathProvider.resolve(appFileType = AppFileType.OPEN_GRAPH)
        val relativePath =
            fileUtils.createPasteRelativePath(
                pasteCoordinate = pasteCoordinate,
                fileName = UrlPasteItem.OPEN_GRAPH_IMAGE,
            )
        userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
    }

fun PasteItem.clear(
    clearResource: Boolean = true,
    userDataPathProvider: UserDataPathProvider,
) {
    when (this) {
        is FilesPasteItem, is ImagesPasteItem -> {
            val pasteFiles = this as PasteFiles
            if (clearResource && pasteFiles.basePath == null) {
                for (path in pasteFiles.getFilePaths(userDataPathProvider)) {
                    fileUtils.deleteFile(path)
                }
            }
        }
        else -> {}
    }
}

fun PasteItem.bindItem(
    pasteCoordinate: PasteCoordinate,
    syncToDownload: Boolean = false,
): PasteItem =
    when (this) {
        is FilesPasteItem -> {
            val (newBasePath, newRelativePathList) = bindFilePaths(pasteCoordinate, syncToDownload)
            FilesPasteItem(
                identifiers = identifiers,
                count = count,
                hash = hash,
                size = size,
                basePath = newBasePath,
                relativePathList = newRelativePathList,
                fileInfoTreeMap = fileInfoTreeMap,
                extraInfo = extraInfo,
            )
        }
        is ImagesPasteItem -> {
            val (newBasePath, newRelativePathList) = bindFilePaths(pasteCoordinate, syncToDownload)
            ImagesPasteItem(
                identifiers = identifiers,
                count = count,
                hash = hash,
                size = size,
                basePath = newBasePath,
                relativePathList = newRelativePathList,
                fileInfoTreeMap = fileInfoTreeMap,
                extraInfo = extraInfo,
            )
        }
        else -> this.bind(pasteCoordinate, syncToDownload)
    }
