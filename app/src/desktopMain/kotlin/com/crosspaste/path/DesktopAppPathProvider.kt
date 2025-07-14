package com.crosspaste.path

import com.crosspaste.app.AppFileType
import com.crosspaste.config.DevConfig
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.getSystemProperty
import com.crosspaste.utils.noOptionParent
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.nio.file.Paths

interface AppPathProvider : PathProvider {

    val userHome: Path

    val pasteAppPath: Path

    val pasteAppJarPath: Path

    val pasteAppExePath: Path

    val pasteUserPath: Path
}

class DesktopPathProvider(
    private val pasteAppPath: Path,
    private val pasteUserPath: Path,
) : PathProvider {

    override fun resolve(
        fileName: String?,
        appFileType: AppFileType,
    ): Path {
        val path =
            when (appFileType) {
                AppFileType.APP -> pasteAppPath
                AppFileType.LOG -> pasteUserPath.resolve("logs")
                AppFileType.ENCRYPT -> pasteUserPath.resolve("encrypt")
                AppFileType.USER -> pasteUserPath
                AppFileType.MODULE -> pasteUserPath.resolve("module")
                else -> pasteAppPath
            }

        autoCreateDir(path)

        return fileName?.let {
            path.resolve(fileName)
        } ?: path
    }
}

class DesktopAppPathProvider(
    private val platform: Platform,
) : AppPathProvider {

    private val appEnvUtils = getAppEnvUtils()

    private val appPathProvider = getAppPathProvider()

    override val userHome: Path = appPathProvider.userHome

    override val pasteAppPath: Path = appPathProvider.pasteAppPath

    override val pasteAppJarPath: Path = appPathProvider.pasteAppJarPath

    override val pasteAppExePath: Path = appPathProvider.pasteAppExePath

    override val pasteUserPath: Path = appPathProvider.pasteUserPath

    override fun resolve(
        fileName: String?,
        appFileType: AppFileType,
    ): Path = appPathProvider.resolve(fileName, appFileType)

    private fun getAppPathProvider(): AppPathProvider =
        if (appEnvUtils.isDevelopment()) {
            DevelopmentAppPathProvider(platform)
        } else if (appEnvUtils.isTest()) {
            // In the test environment, DesktopAppPathProvider will be mocked
            this
        } else {
            if (platform.isWindows()) {
                WindowsAppPathProvider()
            } else if (platform.isMacos()) {
                MacosAppPathProvider()
            } else if (platform.isLinux()) {
                LinuxAppPathProvider()
            } else {
                throw IllegalStateException("Unknown platform: ${platform.name}")
            }
        }
}

class DevelopmentAppPathProvider(
    private val platform: Platform,
) : AppPathProvider {

    private val systemProperty = getSystemProperty()

    private val composeAppDir = systemProperty.get("user.dir")

    override val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    override val pasteAppPath: Path = getAppPath()

    override val pasteAppJarPath: Path = getResources()

    override val pasteAppExePath: Path = getResources()

    override val pasteUserPath: Path = getUserPath()

    private val pathProvider: PathProvider = DesktopPathProvider(pasteAppPath, pasteUserPath)

    private fun getAppPath(): Path =
        DevConfig.pasteAppPath?.let {
            val path = it.toPath(normalize = true)
            if (path.isAbsolute) {
                path
            } else {
                composeAppDir.toPath().resolve(it)
            }
        } ?: composeAppDir.toPath()

    private fun getUserPath(): Path =
        DevConfig.pasteUserPath?.let {
            val path = it.toPath(normalize = true)
            if (path.isAbsolute) {
                path
            } else {
                composeAppDir.toPath().resolve(it)
            }
        } ?: composeAppDir.toPath()

    private fun getResources(): Path {
        val resources = composeAppDir.toPath().resolve("resources")
        val platformAndArch =
            if (platform.isWindows() && platform.is64bit()) {
                "windows-x64"
            } else if (platform.isMacos()) {
                if (platform.arch.contains("x86_64")) {
                    "macos-x64"
                } else {
                    "macos-arm64"
                }
            } else if (platform.isLinux() && platform.is64bit()) {
                "linux-x64"
            } else {
                throw IllegalStateException("Unknown platform: ${platform.name}")
            }
        return resources.resolve(platformAndArch)
    }

    override fun resolve(
        fileName: String?,
        appFileType: AppFileType,
    ): Path = pathProvider.resolve(fileName, appFileType)
}

class WindowsAppPathProvider : AppPathProvider {

    private val systemProperty = getSystemProperty()

    override val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    override val pasteAppPath: Path = getAppJarPath().noOptionParent.normalized()

    override val pasteAppJarPath: Path = getAppJarPath()

    override val pasteAppExePath: Path = getAppExePath()

    override val pasteUserPath: Path = getUserPath()

    private val pathProvider: PathProvider = DesktopPathProvider(pasteAppPath, pasteUserPath)

    private fun getAppJarPath(): Path {
        systemProperty.getOption("compose.application.resources.dir")?.let {
            return it.toPath()
        }
        systemProperty.getOption("skiko.library.path")?.let {
            return it.toPath()
        }
        throw IllegalStateException("Could not find app path")
    }

    private fun getAppExePath(): Path = getAppJarPath().noOptionParent.resolve("bin").normalized()

    private fun getUserPath(): Path = userHome.resolve(".crosspaste")

    override fun resolve(
        fileName: String?,
        appFileType: AppFileType,
    ): Path = pathProvider.resolve(fileName, appFileType)
}

class MacosAppPathProvider : AppPathProvider {

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

    override val pasteAppExePath: Path = getAppExePath()

    override val pasteUserPath: Path = getUserPath()

    private val pathProvider: PathProvider = DesktopPathProvider(pasteAppPath, pasteUserPath)

    private fun getAppJarPath(): Path {
        systemProperty.getOption("compose.application.resources.dir")?.let {
            return it.toPath()
        }
        systemProperty.getOption("skiko.library.path")?.let {
            return it.toPath()
        }
        throw IllegalStateException("Could not find app path")
    }

    private fun getAppExePath(): Path =
        getAppJarPath()
            .noOptionParent
            .resolve("runtime")
            .resolve("Contents")
            .resolve("Home")
            .resolve("lib")
            .normalized()

    private fun getUserPath(): Path {
        val appSupportPath =
            userHome
                .resolve("Library")
                .resolve("Application Support")
                .resolve("CrossPaste")
        val appSupportNioPath = appSupportPath.toNioPath()
        if (Files.notExists(appSupportNioPath)) {
            Files.createDirectories(appSupportNioPath)
        }

        return appSupportPath
    }

    override fun resolve(
        fileName: String?,
        appFileType: AppFileType,
    ): Path = pathProvider.resolve(fileName, appFileType)
}

class LinuxAppPathProvider : AppPathProvider {

    private val systemProperty = getSystemProperty()

    override val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    override val pasteAppPath: Path = getAppJarPath().noOptionParent.noOptionParent.normalized()

    override val pasteAppJarPath: Path = getAppJarPath()

    override val pasteAppExePath: Path = getAppExePath()

    override val pasteUserPath: Path = getUserPath()

    private val pathProvider: PathProvider = DesktopPathProvider(pasteAppPath, pasteUserPath)

    private fun getAppJarPath(): Path {
        systemProperty.getOption("compose.application.resources.dir")?.let {
            return it.toPath()
        }
        systemProperty.getOption("skiko.library.path")?.let {
            return it.toPath()
        }
        throw IllegalStateException("Could not find app path")
    }

    private fun getAppExePath(): Path =
        getAppJarPath()
            .noOptionParent
            .resolve("runtime")
            .resolve("lib")
            .normalized()

    private fun getUserPath(): Path = userHome.resolve(".local").resolve("shard").resolve(".crosspaste")

    override fun resolve(
        fileName: String?,
        appFileType: AppFileType,
    ): Path = pathProvider.resolve(fileName, appFileType)
}
