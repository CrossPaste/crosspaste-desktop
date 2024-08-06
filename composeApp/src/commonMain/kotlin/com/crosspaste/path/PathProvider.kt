package com.crosspaste.path

import com.crosspaste.app.AppFileType
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.presist.DirFileInfoTree
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.utils.FileUtils
import okio.Path
import okio.Path.Companion.toPath

interface PathProvider {
    fun resolve(
        fileName: String? = null,
        appFileType: AppFileType,
    ): Path {
        val path =
            when (appFileType) {
                AppFileType.APP -> pasteAppPath
                AppFileType.USER -> pasteUserPath
                AppFileType.LOG -> pasteLogPath.resolve("logs")
                AppFileType.ENCRYPT -> pasteEncryptPath.resolve("encrypt")
                AppFileType.DATA -> pasteDataPath.resolve("data")
                AppFileType.HTML -> pasteUserPath.resolve("html")
                AppFileType.ICON -> pasteUserPath.resolve("icons")
                AppFileType.FAVICON -> pasteUserPath.resolve("favicon")
                AppFileType.FILE_EXT_ICON -> pasteUserPath.resolve("file_ext_icons")
                AppFileType.IMAGE -> pasteUserPath.resolve("images")
                AppFileType.VIDEO -> pasteUserPath.resolve("videos")
                AppFileType.FILE -> pasteUserPath.resolve("files")
                AppFileType.KCEF -> pasteUserPath.resolve("kcef")
            }

        autoCreateDir(path)

        return fileName?.let {
            path.resolve(fileName)
        } ?: path
    }

    fun resolve(
        basePath: Path,
        path: String,
        autoCreate: Boolean = true,
        isFile: Boolean = false,
    ): Path {
        val newPath = basePath.resolve(path)
        if (autoCreate) {
            if (isFile) {
                newPath.parent?.let { autoCreateDir(it) }
            } else {
                autoCreateDir(newPath)
            }
        }
        return newPath
    }

    fun resolve(
        appInstanceId: String,
        dateString: String,
        pasteId: Long,
        pasteFiles: PasteFiles,
        isPull: Boolean,
        filesIndexBuilder: FilesIndexBuilder? = null,
    ) {
        val basePath =
            pasteFiles.basePath?.toPath() ?: run {
                resolve(appFileType = pasteFiles.getAppFileType())
                    .resolve(appInstanceId)
                    .resolve(dateString)
                    .resolve(pasteId.toString())
            }

        if (isPull) {
            autoCreateDir(basePath)
        }

        val fileInfoTreeMap = pasteFiles.getFileInfoTreeMap()

        for (filePath in pasteFiles.getFilePaths()) {
            fileInfoTreeMap[filePath.name]?.let {
                resolveFileInfoTree(basePath, filePath.name, it, isPull, filesIndexBuilder)
            }
        }
    }

    private fun resolveFileInfoTree(
        basePath: Path,
        name: String,
        fileInfoTree: FileInfoTree,
        isPull: Boolean,
        filesIndexBuilder: FilesIndexBuilder?,
    ) {
        if (fileInfoTree.isFile()) {
            val filePath = basePath.resolve(name)
            if (isPull) {
                if (!fileUtils.createEmptyPasteFile(filePath, fileInfoTree.size)) {
                    throw PasteException(
                        StandardErrorCode.CANT_CREATE_FILE.toErrorCode(),
                        "Failed to create file: $filePath",
                    )
                }
            }
            filesIndexBuilder?.addFile(filePath, fileInfoTree.size)
        } else {
            val dirPath = basePath.resolve(name)
            if (isPull) {
                autoCreateDir(dirPath)
            }
            val dirFileInfoTree = fileInfoTree as DirFileInfoTree
            dirFileInfoTree.iterator().forEach { (subName, subFileInfoTree) ->
                resolveFileInfoTree(dirPath, subName, subFileInfoTree, isPull, filesIndexBuilder)
            }
        }
    }

    private fun autoCreateDir(path: Path) {
        if (!path.toFile().exists()) {
            if (!path.toFile().mkdirs()) {
                throw PasteException(StandardErrorCode.CANT_CREATE_DIR.toErrorCode(), "Failed to create directory: $path")
            }
        }
    }

    val fileUtils: FileUtils

    val userHome: Path

    val pasteAppPath: Path

    val pasteAppJarPath: Path

    val pasteUserPath: Path

    val pasteLogPath: Path get() = pasteUserPath

    val pasteEncryptPath get() = pasteUserPath

    val pasteDataPath get() = pasteUserPath
}
