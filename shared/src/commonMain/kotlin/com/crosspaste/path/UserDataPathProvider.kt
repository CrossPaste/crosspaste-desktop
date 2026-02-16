package com.crosspaste.path

import com.crosspaste.app.AppFileType
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.presist.DirFileInfoTree
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import okio.Path.Companion.toPath

class UserDataPathProvider(
    private val configManager: CommonConfigManager,
    private val platformUserDataPathProvider: PlatformUserDataPathProvider,
) : PathProvider {

    private val logger = KotlinLogging.logger {}

    override val fileUtils: FileUtils = getFileUtils()

    override fun resolve(
        fileName: String?,
        appFileType: AppFileType,
    ): Path =
        resolve(fileName, appFileType) {
            getUserDataPath()
        }

    fun resolve(
        fileName: String?,
        appFileType: AppFileType,
        getBasePath: () -> Path,
    ): Path {
        val basePath = getBasePath()
        val path =
            when (appFileType) {
                AppFileType.FILE -> basePath.resolve("files")
                AppFileType.IMAGE -> basePath.resolve("images")
                AppFileType.DATA -> basePath.resolve("data")
                AppFileType.HTML -> basePath.resolve("html")
                AppFileType.OPEN_GRAPH -> basePath.resolve("opengraph")
                AppFileType.RTF -> basePath.resolve("rtf")
                AppFileType.ICON -> basePath.resolve("icons")
                AppFileType.FAVICON -> basePath.resolve("favicon")
                AppFileType.FILE_EXT_ICON -> basePath.resolve("file_ext_icons")
                AppFileType.VIDEO -> basePath.resolve("videos")
                AppFileType.TEMP -> basePath.resolve("temp")
                AppFileType.MARKETING -> basePath.resolve("marketing")
                else -> basePath
            }

        autoCreateDir(path)

        return fileName?.let {
            path.resolve(fileName)
        } ?: path
    }

    fun cleanTemp() {
        runCatching {
            val tempPath = resolve(appFileType = AppFileType.TEMP)
            fileUtils.fileSystem.deleteRecursively(tempPath)
        }.onFailure { e ->
            logger.warn(e) { "Failed to clean temp directory" }
        }
    }

    fun resolve(
        appInstanceId: String,
        dateString: String,
        pasteId: Long,
        pasteFiles: PasteFiles,
        isPull: Boolean,
        filesIndexBuilder: FilesIndexBuilder?,
        resolveConflicts: Boolean = true,
    ): Map<String, String> {
        val renameMap = mutableMapOf<String, String>()
        val isDownloadDir = pasteFiles.basePath != null
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

        val fileInfoTreeMap = pasteFiles.fileInfoTreeMap

        for (filePath in pasteFiles.getFilePaths(this)) {
            val originalName = filePath.name
            fileInfoTreeMap[originalName]?.let { fileInfoTree ->
                val resolvedName =
                    if (isPull && isDownloadDir && resolveConflicts) {
                        val resolved = fileUtils.resolveNonConflictFileName(basePath, originalName)
                        if (resolved != originalName) {
                            renameMap[originalName] = resolved
                        }
                        resolved
                    } else {
                        originalName
                    }
                resolveFileInfoTree(basePath, resolvedName, fileInfoTree, isPull, filesIndexBuilder)
            }
        }

        return renameMap
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
                if (fileUtils.createEmptyPasteFile(filePath, fileInfoTree.size).isFailure) {
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

    fun getUserDataPath(): Path =
        if (configManager.getCurrentConfig().useDefaultStoragePath) {
            platformUserDataPathProvider.getUserDefaultStoragePath()
        } else {
            configManager.getCurrentConfig().storagePath.toPath(normalize = true)
        }
}
