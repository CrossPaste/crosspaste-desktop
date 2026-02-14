package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getPlatformUtils
import okio.Path
import okio.Path.Companion.toPath

interface PasteFiles {

    // The number of files in this paste item
    val count: Long

    val basePath: String?

    val fileInfoTreeMap: Map<String, FileInfoTree>

    val relativePathList: List<String>

    val size: Long

    fun getAppFileType(): AppFileType

    fun getFilePaths(userDataPathProvider: UserDataPathProvider): List<Path>

    fun getDirectChildrenCount(): Long = fileInfoTreeMap.size.toLong()

    fun isRefFiles(): Boolean = basePath != null

    fun hasExistingFiles(): Boolean {
        val base = basePath ?: return true
        val fileUtils = getFileUtils()
        return relativePathList.any { relativePath ->
            fileUtils.existFile(base.toPath() / relativePath.toPath().name)
        }
    }

    fun isInDownloads(): Boolean {
        val base = basePath ?: return false
        return base == getPlatformUtils().getSystemDownloadDir().toString()
    }

    fun applyRenameMap(renameMap: Map<String, String>): PasteFiles

    fun computeRenamedFileData(renameMap: Map<String, String>): Pair<List<String>, Map<String, FileInfoTree>> {
        val newRelativePathList = relativePathList.map { renameMap[it] ?: it }
        val newFileInfoTreeMap = fileInfoTreeMap.entries.associate { (k, v) -> (renameMap[k] ?: k) to v }
        return newRelativePathList to newFileInfoTreeMap
    }

    fun bindFilePaths(
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
}
