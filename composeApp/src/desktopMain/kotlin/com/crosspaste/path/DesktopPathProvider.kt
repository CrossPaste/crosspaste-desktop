package com.crosspaste.path

import com.crosspaste.app.AppEnv
import com.crosspaste.platform.currentPlatform
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getResourceUtils
import com.crosspaste.utils.getSystemProperty
import com.crosspaste.utils.noOptionParent
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.nio.file.Paths

object DesktopPathProvider : PathProvider {

    private val pathProvider = getPathProvider()

    override val fileUtils: FileUtils = getFileUtils()

    override val userHome: Path get() = pathProvider.userHome

    override val pasteAppPath: Path get() = pathProvider.pasteAppPath

    override val pasteAppJarPath: Path get() = pathProvider.pasteAppJarPath

    override val pasteUserPath: Path get() = pathProvider.pasteUserPath

    override val pasteLogPath: Path get() = pathProvider.pasteLogPath

    override val pasteEncryptPath get() = pathProvider.pasteEncryptPath

    override val pasteDataPath get() = pathProvider.pasteDataPath

    private fun getPathProvider(): PathProvider {
        return if (AppEnv.CURRENT.isDevelopment()) {
            DevelopmentPathProvider()
        } else if (AppEnv.CURRENT.isTest()) {
            // In the test environment, DesktopPathProvider will be mocked
            this
        } else {
            if (currentPlatform().isWindows()) {
                WindowsPathProvider()
            } else if (currentPlatform().isMacos()) {
                MacosPathProvider()
            } else if (currentPlatform().isLinux()) {
                LinuxPathProvider()
            } else {
                throw IllegalStateException("Unknown platform: ${currentPlatform().name}")
            }
        }
    }
}

class DevelopmentPathProvider : PathProvider {

    private val systemProperty = getSystemProperty()

    private val composeAppDir = systemProperty.get("user.dir")

    private val resourceUtils = getResourceUtils()

    private val development = resourceUtils.loadProperties("development.properties")

    override val pasteAppPath: Path = getAppPath()

    override val pasteAppJarPath: Path = getAppPath()

    override val pasteUserPath: Path = getUserPath()

    override val fileUtils: FileUtils = getFileUtils()

    override val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    private fun getAppPath(): Path {
        development.getProperty("pasteAppPath")?.let {
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

    private fun getUserPath(): Path {
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

class WindowsPathProvider : PathProvider {

    private val systemProperty = getSystemProperty()

    override val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    override val pasteAppPath: Path = getAppJarPath().noOptionParent.normalized()

    override val pasteAppJarPath: Path = getAppJarPath()

    override val pasteUserPath: Path = userHome.resolve(".crosspaste")

    override val fileUtils: FileUtils = getFileUtils()

    private fun getAppJarPath(): Path {
        systemProperty.getOption("compose.application.resources.dir")?.let {
            return it.toPath()
        }
        systemProperty.getOption("skiko.library.path")?.let {
            return it.toPath()
        }
        throw IllegalStateException("Could not find app path")
    }
}

class MacosPathProvider : PathProvider {

    /**
     * .
     * ├── Info.plist
     * ├── MacOS
     * ├── PkgInfo
     * ├── Resources
     * ├── _CodeSignature
     * ├── app
     * └── runtime
     */
    private val systemProperty = getSystemProperty()

    override val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    override val pasteAppPath: Path = getAppJarPath().noOptionParent.noOptionParent.normalized()

    override val pasteAppJarPath: Path = getAppJarPath()

    override val pasteUserPath: Path = getAppSupportPath()

    override val pasteLogPath: Path = getLogPath()

    override val fileUtils: FileUtils = getFileUtils()

    private fun getAppSupportPath(): Path {
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

    private fun getLogPath(): Path {
        val appLogsPath =
            userHome
                .resolve("Library")
                .resolve("Logs")
                .resolve("CrossPaste")
        val appLogsNioPath = appLogsPath.toNioPath()

        if (Files.notExists(appLogsNioPath)) {
            Files.createDirectories(appLogsNioPath)
        }

        return appLogsPath
    }

    private fun getAppJarPath(): Path {
        systemProperty.getOption("compose.application.resources.dir")?.let {
            return it.toPath()
        }
        systemProperty.getOption("skiko.library.path")?.let {
            return it.toPath()
        }
        throw IllegalStateException("Could not find app path")
    }
}

class LinuxPathProvider : PathProvider {

    private val systemProperty = getSystemProperty()

    override val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    override val pasteAppPath: Path = getAppJarPath().noOptionParent.noOptionParent.normalized()

    override val pasteAppJarPath: Path = getAppJarPath()

    override val pasteUserPath: Path = userHome.resolve(".local").resolve("shard").resolve(".crosspaste")

    override val fileUtils: FileUtils = getFileUtils()

    private fun getAppJarPath(): Path {
        systemProperty.getOption("compose.application.resources.dir")?.let {
            return it.toPath()
        }
        systemProperty.getOption("skiko.library.path")?.let {
            return it.toPath()
        }
        throw IllegalStateException("Could not find app path")
    }
}
