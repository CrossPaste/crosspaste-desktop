package com.crosspaste.path

import com.crosspaste.config.DevConfig
import com.crosspaste.path.LinuxAppPathProvider.Companion.LOCAL
import com.crosspaste.path.LinuxAppPathProvider.Companion.SHARE
import com.crosspaste.path.PlatformUserDataPathProvider.Companion.CROSSPASTE_DIR_NAME
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getSystemProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import kotlin.io.path.createTempDirectory

fun getPlatformPathProvider(platform: Platform): PlatformUserDataPathProvider {
    val appEnvUtils = getAppEnvUtils()
    return if (appEnvUtils.isDevelopment()) {
        DevelopmentPlatformUserDataPathProvider()
    } else if (appEnvUtils.isTest()) {
        // In the test environment, DesktopPathProvider will be mocked
        TestPlatformUserDataPathProvider()
    } else {
        if (platform.isWindows()) {
            WindowsPlatformUserDataPathProvider()
        } else if (platform.isMacos()) {
            MacosPlatformUserDataPathProvider()
        } else if (platform.isLinux()) {
            LinuxPlatformUserDataPathProvider()
        } else {
            throw IllegalStateException("Unknown platform: ${platform.name}")
        }
    }
}

class DevelopmentPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val composeAppDir = systemProperty.get("user.dir")

    override fun getUserDefaultStoragePath(): Path =
        DevConfig.pasteUserPath?.let {
            val path = it.toPath(normalize = true)
            if (path.isAbsolute) {
                path
            } else {
                composeAppDir.toPath().resolve(it)
            }
        } ?: composeAppDir.toPath()
}

class TestPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val tempDir = createTempDirectory(CROSSPASTE_DIR_NAME)

    override fun getUserDefaultStoragePath(): Path = tempDir.toOkioPath(normalize = true)
}

class WindowsPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val userHome: Path = systemProperty.get("user.home").toPath(normalize = true)

    override fun getUserDefaultStoragePath(): Path = userHome.resolve(CROSSPASTE_DIR_NAME)
}

class MacosPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    private val systemProperty = getSystemProperty()

    private val fileUtils = getFileUtils()

    private val userHome: Path = systemProperty.get("user.home").toPath(normalize = true)

    override fun getUserDefaultStoragePath(): Path {
        val appSupportPath =
            userHome
                .resolve("Library")
                .resolve("Application Support")
                .resolve("CrossPaste")
        if (!fileUtils.existFile(appSupportPath)) {
            fileUtils.createDir(appSupportPath).getOrThrow()
        }
        return appSupportPath
    }
}

class LinuxPlatformUserDataPathProvider : PlatformUserDataPathProvider {

    companion object {
        val fileUtils = getFileUtils()
    }

    private val logger = KotlinLogging.logger {}

    private val migrateLock = Mutex()

    @Volatile private var migrationChecked = false

    private val systemProperty = getSystemProperty()

    private val userHome: Path = systemProperty.get("user.home").toPath(normalize = true)

    override fun getUserDefaultStoragePath(): Path {
        // Migrate from ~/.local/shard/.crosspaste to ~/.local/share/.crosspaste
        val oldPath = userHome.resolve(LOCAL).resolve("shard").resolve(CROSSPASTE_DIR_NAME)
        val newPath = userHome.resolve(LOCAL).resolve(SHARE).resolve(CROSSPASTE_DIR_NAME)

        if (!migrationChecked) {
            runBlockingMigrationOnce(oldPath, newPath)
        }

        return newPath
    }

    private fun runBlockingMigrationOnce(
        oldPath: Path,
        newPath: Path,
    ) {
        // double-check locking
        if (migrationChecked) return

        runBlocking {
            migrateLock.withLock {
                if (migrationChecked) return@withLock

                val oldExists = fileUtils.existFile(oldPath)
                val newExists = fileUtils.existFile(newPath)

                if (oldExists && !newExists) {
                    runCatching {
                        fileUtils.createDir(newPath)
                        fileUtils.moveFile(oldPath, newPath)
                    }.onFailure { e ->
                        logger.error(e) { "Error migration from $oldPath move to $newPath" }
                        runCatching {
                            fileUtils.fileSystem.copy(oldPath, newPath)
                        }.onFailure { e2 ->
                            logger.error(e2) { "Error migration from $oldPath copy to $newPath" }
                        }
                    }
                }

                migrationChecked = true
            }
        }
    }
}
