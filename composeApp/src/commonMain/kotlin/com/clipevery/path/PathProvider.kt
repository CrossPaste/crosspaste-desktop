package com.clipevery.path

import com.clipevery.app.AppFileType
import com.clipevery.clip.item.ClipFiles
import com.clipevery.exception.ClipException
import com.clipevery.exception.StandardErrorCode
import com.clipevery.presist.DirFileInfoTree
import com.clipevery.presist.FileInfoTree
import com.clipevery.presist.FilesIndexBuilder
import com.clipevery.utils.FileUtils
import java.nio.file.Path

interface PathProvider {
    fun resolve(fileName: String? = null, appFileType: AppFileType): Path {
        val path = when (appFileType) {
            AppFileType.APP -> clipAppPath
            AppFileType.USER -> clipUserPath
            AppFileType.LOG -> clipLogPath.resolve("logs")
            AppFileType.ENCRYPT -> clipEncryptPath.resolve("encrypt")
            AppFileType.DATA -> clipDataPath.resolve("data")
            AppFileType.HTML -> clipUserPath.resolve("html")
            AppFileType.IMAGE -> clipUserPath.resolve("images")
            AppFileType.VIDEO -> clipUserPath.resolve("videos")
            AppFileType.FILE -> clipUserPath.resolve("files")
            AppFileType.KCEF -> clipUserPath.resolve("kcef")
        }

        autoCreateDir(path)

        return fileName?.let {
            path.resolve(fileName)
        } ?: path
    }

    fun resolve(basePath: Path,
                path: String,
                autoCreate: Boolean = true,
                isFile: Boolean = false): Path {
        val newPath = basePath.resolve(path)
        if (autoCreate) {
            if (isFile) {
                autoCreateDir(newPath.parent)
            } else {
                autoCreateDir(newPath)
            }
        }
        return newPath
    }

    fun resolve(appInstanceId: String,
                clipId: Int,
                clipFiles: ClipFiles,
                filesIndexBuilder: FilesIndexBuilder? = null) {
        val basePath = resolve(appFileType = clipFiles.getAppFileType())
        val clipIdPath = basePath.resolve(appInstanceId).resolve(clipId.toString())
        autoCreateDir(clipIdPath)

        val fileInfoTreeMap = clipFiles.getFileInfoTreeMap()
        for (fileInfoTreeEntry in fileInfoTreeMap) {
            resolveFileInfoTree(clipIdPath, fileInfoTreeEntry.key, fileInfoTreeEntry.value, filesIndexBuilder)
        }
    }

    private fun resolveFileInfoTree(basePath: Path, name: String, fileInfoTree: FileInfoTree, filesIndexBuilder: FilesIndexBuilder?) {
        if (fileInfoTree.isFile()) {
            val filePath = basePath.resolve(name)
            if (!fileUtils.createEmptyClipFile(filePath, fileInfoTree.size)) {
                throw ClipException(StandardErrorCode.CANT_CREATE_FILE.toErrorCode(), "Failed to create file: $filePath")
            }
            filesIndexBuilder?.addFile(filePath, fileInfoTree.size)
        } else {
            val dirPath = basePath.resolve(name)
            autoCreateDir(dirPath)
            val dirFileInfoTree = fileInfoTree as DirFileInfoTree
            dirFileInfoTree.getTree().forEach { (subName, subFileInfoTree) ->
                resolveFileInfoTree(dirPath, subName, subFileInfoTree, filesIndexBuilder)
            }
        }
    }

    private fun autoCreateDir(path: Path) {
        if (!path.toFile().exists()) {
            if (!path.toFile().mkdirs()) {
                throw ClipException(StandardErrorCode.CANT_CREATE_DIR.toErrorCode(), "Failed to create directory: $path")
            }
        }
    }

    val fileUtils: FileUtils

    val clipAppPath: Path

    val clipUserPath: Path

    val clipLogPath: Path get() = clipUserPath

    val clipEncryptPath get() = clipUserPath

    val clipDataPath get() = clipUserPath
}