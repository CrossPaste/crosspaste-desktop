package com.crosspaste.path

import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppFileType
import com.crosspaste.config.ConfigManager
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.platform.currentPlatform
import com.crosspaste.presist.DirFileInfoTree
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getSystemProperty
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createTempDirectory

class DesktopUserDataPathProvider(private val configManager: ConfigManager) : UserDataPathProvider {

    private val platformUserDataPathProvider: PlatformUserDataPathProvider = getPlatformPathProvider()

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

    override fun migration(
        migrationPath: Path,
        realmMigrationAction: (Path) -> Unit,
    ) {
        try {
            for (type in types) {
                if (type == AppFileType.DATA) {
                    continue
                }
                val originTypePath = resolve(appFileType = type)
                val migrationTypePath =
                    resolve(fileName = null, appFileType = type) {
                        migrationPath
                    }
                originTypePath.toFile()
                    .copyRecursively(migrationTypePath.toFile(), true)
            }
            realmMigrationAction(
                resolve(fileName = null, appFileType = AppFileType.DATA) {
                    migrationPath
                },
            )
            try {
                for (type in types) {
                    val originTypePath = resolve(appFileType = type)
                    originTypePath.toFile().deleteRecursively()
                }
            } catch (ignore: Exception) {
            }
            configManager.updateConfig(
                listOf("storagePath", "useDefaultStoragePath"),
                listOf(migrationPath.toString(), false),
            )
        } catch (e: Exception) {
            try {
                migrationPath.toFile().listFiles()?.forEach {
                    it.deleteRecursively()
                }
            } catch (ignore: Exception) {
            }
            throw e
        }
    }

    override fun cleanTemp() {
        try {
            val tempPath = resolve(appFileType = AppFileType.TEMP)
            tempPath.toFile().deleteRecursively()
        } catch (ignore: Exception) {
        }
    }

    override fun resolve(
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

    override fun getUserDataPath(): Path {
        return if (configManager.config.useDefaultStoragePath) {
            platformUserDataPathProvider.getUserDefaultStoragePath()
        } else {
            configManager.config.storagePath.toPath(normalize = true)
        }
    }

    fun getPlatformPathProvider(): PlatformUserDataPathProvider {
        return if (AppEnv.CURRENT.isDevelopment()) {
            DevelopmentPlatformUserDataPathProvider()
        } else if (AppEnv.CURRENT.isTest()) {
            // In the test environment, DesktopPathProvider will be mocked
            TestPlatformUserDataPathProvider()
        } else {
            val platform = currentPlatform()
            if (platform.isWindows()) {
                WindowsPlatformUserDataPathProvider()
            } else if (platform.isMacos()) {
                MacosPlatformUserDataPathProvider()
            } else if (platform.isLinux()) {
                LinuxPlatformUserDataPathProvider()
            } else {
                throw IllegalStateException("Unknown platform: ${currentPlatform().name}")
            }
        }
    }
}

interface PlatformUserDataPathProvider {

    fun getUserDefaultStoragePath(): Path
}

class DevelopmentPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val composeAppDir = systemProperty.get("user.dir")

    private val development = DesktopResourceUtils.loadProperties("development.properties")

    override fun getUserDefaultStoragePath(): Path {
        development.getProperty("pasteUserPath")?.let {
            val path = it.toPath(normalize = true)
            if (path.isAbsolute) {
                return path
            } else {
                return composeAppDir.toPath().resolve(it)
            }
        } ?: run {
            return composeAppDir.toPath()
        }
    }
}

class TestPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val tempDir = createTempDirectory(".crosspaste")

    override fun getUserDefaultStoragePath(): Path {
        return tempDir.toOkioPath(normalize = true)
    }
}

class WindowsPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    override fun getUserDefaultStoragePath(): Path {
        return userHome.resolve(".crosspaste")
    }
}

class MacosPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    override fun getUserDefaultStoragePath(): Path {
        val appSupportPath =
            userHome.resolve("Library")
                .resolve("Application Support")
                .resolve("CrossPaste")
        val appSupportNioPath = appSupportPath.toNioPath()
        if (Files.notExists(appSupportNioPath)) {
            Files.createDirectories(appSupportNioPath)
        }

        return appSupportPath
    }
}

class LinuxPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    override fun getUserDefaultStoragePath(): Path {
        return userHome.resolve(".local").resolve("shard").resolve(".crosspaste")
    }
}
