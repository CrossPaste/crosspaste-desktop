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

    fun bindFilePaths(
        pasteCoordinate: PasteCoordinate,
        isLargeFile: Boolean,
    ): Pair<String?, List<String>> {
        val fileUtils = getFileUtils()
        val downloadDir =
            if (isLargeFile) getPlatformUtils().getSystemDownloadDir() else null
        val newRelativePathList =
            relativePathList.map { relativePath ->
                val fileName = relativePath.toPath().name
                if (isLargeFile) {
                    fileUtils.resolveNonConflictFileName(downloadDir!!, fileName)
                } else {
                    fileUtils.createPasteRelativePath(
                        pasteCoordinate = pasteCoordinate,
                        fileName = fileName,
                    )
                }
            }
        return downloadDir?.toString() to newRelativePathList
    }
}
