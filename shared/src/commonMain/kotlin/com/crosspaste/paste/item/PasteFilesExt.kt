package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.config.isInside
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
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
 * Name of the folder a file lives in when it sits outside managed storage, or
 * null when it lives in managed storage. The folder is read back from the row's
 * own [PasteFiles.basePath] rather than the current config so existing rows keep
 * showing the folder they were actually written to.
 */
fun PasteFiles.externalFolderName(userDataPathProvider: UserDataPathProvider): String? {
    val base = basePath?.takeIf { it.isNotBlank() }?.toPath(normalize = true) ?: return null
    if (isInside(base, userDataPathProvider.getUserDataPath())) {
        return null
    }
    return base.name.ifEmpty { base.toString() }.takeIf { it.isNotEmpty() }
}

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

/**
 * Propagate conflict-resolved file renames (e.g. `"a.txt"` -> `"a(1).txt"`) into
 * [PasteData.pasteAppearItem] and [PasteData.pasteCollection] so stored metadata
 * matches the actual filenames allocated on disk.
 */
fun PasteData.applyRenameMap(renameMap: Map<String, String>): PasteData {
    if (renameMap.isEmpty()) return this
    val updatedAppearItem =
        (pasteAppearItem as? PasteFiles)?.applyRenameMap(renameMap) as? PasteItem
            ?: pasteAppearItem
    val updatedCollectionItems =
        pasteCollection.pasteItems.map { item ->
            (item as? PasteFiles)?.applyRenameMap(renameMap) as? PasteItem ?: item
        }
    return copy(
        pasteAppearItem = updatedAppearItem,
        pasteCollection = PasteCollection(updatedCollectionItems),
    )
}
