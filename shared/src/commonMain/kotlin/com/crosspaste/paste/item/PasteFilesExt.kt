package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
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

/**
 * Name of the folder a received file was written to when it went outside managed
 * storage, or null when it lives in managed storage. The folder is whatever the
 * large-file destination was at receive time, so it cannot be recomputed from the
 * current config — it is read back from the row's own basePath.
 */
fun PasteFiles.externalFolderName(): String? = basePath?.toPath()?.name

/**
 * [destinationPath] is the absolute directory the files must be written to, or
 * null to lay them out under managed storage. A non-null destination is stored as
 * the row's basePath so the row keeps resolving there even if the setting changes.
 */
fun PasteFiles.bindFilePaths(
    pasteCoordinate: PasteCoordinate,
    destinationPath: String?,
): Pair<String?, List<String>> {
    val fileUtils = getFileUtils()
    val newRelativePathList =
        relativePathList.map { relativePath ->
            val fileName = relativePath.toPath().name
            if (destinationPath != null) {
                fileName
            } else {
                fileUtils.createPasteRelativePath(
                    pasteCoordinate = pasteCoordinate,
                    fileName = fileName,
                )
            }
        }
    return destinationPath to newRelativePathList
}
