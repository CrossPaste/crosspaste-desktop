package com.crosspaste.path

import com.crosspaste.app.AppFileType
import com.crosspaste.config.ConfigManager
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.presist.DirFileInfoTree
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getFileUtils
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class UserDataPathProvider(
    private val configManager: ConfigManager,
    private val platformUserDataPathProvider: PlatformUserDataPathProvider,
) : PathProvider {

    override val fileUtils: FileUtils = getFileUtils()

    private val types: List<AppFileType> =
        listOf(
            AppFileType.FILE,
            AppFileType.IMAGE,
            AppFileType.DATA,
            AppFileType.HTML,
            AppFileType.ICON,
            AppFileType.FAVICON,
            AppFileType.FILE_EXT_ICON,
            AppFileType.VIDEO,
            AppFileType.TEMP,
        )

    override fun resolve(
        fileName: String?,
        appFileType: AppFileType,
    ): Path {
        return resolve(fileName, appFileType) {
            getUserDataPath()
        }
    }

    private fun resolve(
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
                AppFileType.ICON -> basePath.resolve("icons")
                AppFileType.FAVICON -> basePath.resolve("favicon")
                AppFileType.FILE_EXT_ICON -> basePath.resolve("file_ext_icons")
                AppFileType.VIDEO -> basePath.resolve("videos")
                AppFileType.TEMP -> basePath.resolve("temp")
                else -> basePath
            }

        autoCreateDir(path)

        return fileName?.let {
            path.resolve(fileName)
        } ?: path
    }

    fun migration(
        migrationPath: Path,
        realmMigrationAction: (Path) -> Unit,
    ) {
        try {
            val fileSystem = FileSystem.SYSTEM
            for (type in types) {
                if (type == AppFileType.DATA) {
                    continue
                }
                val originTypePath = resolve(appFileType = type)
                val migrationTypePath =
                    resolve(fileName = null, appFileType = type) {
                        migrationPath
                    }
                fileUtils.copyPath(originTypePath, migrationTypePath)
            }
            realmMigrationAction(
                resolve(fileName = null, appFileType = AppFileType.DATA) {
                    migrationPath
                },
            )
            try {
                for (type in types) {
                    val originTypePath = resolve(appFileType = type)
                    fileSystem.deleteRecursively(originTypePath)
                }
            } catch (ignore: Exception) {
            }
            configManager.updateConfig(
                listOf("storagePath", "useDefaultStoragePath"),
                listOf(migrationPath.toString(), false),
            )
        } catch (e: Exception) {
            try {
                val fileSystem = FileSystem.SYSTEM
                fileSystem.list(migrationPath).forEach { subPath ->
                    if (fileSystem.metadata(subPath).isDirectory) {
                        fileSystem.deleteRecursively(subPath)
                    } else {
                        fileSystem.delete(subPath)
                    }
                }
            } catch (ignore: Exception) {
            }
            throw e
        }
    }

    fun cleanTemp() {
        try {
            val fileSystem = FileSystem.SYSTEM
            val tempPath = resolve(appFileType = AppFileType.TEMP)
            fileSystem.deleteRecursively(tempPath)
        } catch (ignore: Exception) {
        }
    }

    fun resolve(
        appInstanceId: String,
        dateString: String,
        pasteId: Long,
        pasteFiles: PasteFiles,
        isPull: Boolean,
        filesIndexBuilder: FilesIndexBuilder?,
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

        for (filePath in pasteFiles.getFilePaths(this)) {
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

    fun getUserDataPath(): Path {
        return if (configManager.config.useDefaultStoragePath) {
            platformUserDataPathProvider.getUserDefaultStoragePath()
        } else {
            configManager.config.storagePath.toPath(normalize = true)
        }
    }
}
