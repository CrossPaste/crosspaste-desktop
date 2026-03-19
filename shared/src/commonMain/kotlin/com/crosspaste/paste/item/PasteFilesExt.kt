package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getPlatformUtils
import okio.Path
import okio.Path.Companion.toPath

fun PasteFiles.getAppFileType(): AppFileType =
    when (this) {
        is FilesPasteItem -> AppFileType.FILE
        is ImagesPasteItem -> AppFileType.IMAGE
        else -> AppFileType.FILE
    }

fun PasteFiles.getFilePaths(userDataPathProvider: UserDataPathProvider): List<Path> {
    val basePath = basePath?.toPath() ?: userDataPathProvider.resolve(appFileType = getAppFileType())
    return relativePathList.map { relativePath ->
        userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
    }
}

fun PasteFiles.hasExistingFiles(): Boolean {
    val base = basePath ?: return true
    val fileUtils = getFileUtils()
    return relativePathList.any { relativePath ->
        fileUtils.existFile(base.toPath() / relativePath.toPath().name)
    }
}

fun PasteFiles.isInDownloads(): Boolean {
    val base = basePath ?: return false
    return base == getPlatformUtils().getSystemDownloadDir().toString()
}

fun PasteFiles.bindFilePaths(
    pasteCoordinate: PasteCoordinate,
    syncToDownload: Boolean,
): Pair<String?, List<String>> {
    val fileUtils = getFileUtils()
    val newBasePath =
        if (syncToDownload) getPlatformUtils().getSystemDownloadDir().toString() else null
    val newRelativePathList =
        relativePathList.map { relativePath ->
            val fileName = relativePath.toPath().name
            if (syncToDownload) {
                fileName
            } else {
                fileUtils.createPasteRelativePath(
                    pasteCoordinate = pasteCoordinate,
                    fileName = fileName,
                )
            }
        }
    return newBasePath to newRelativePathList
}
