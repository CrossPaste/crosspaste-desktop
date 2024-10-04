package com.crosspaste.path

import com.crosspaste.app.AppFileType
import com.crosspaste.platform.getPlatform
import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.getSystemProperty
import com.crosspaste.utils.noOptionParent
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.nio.file.Paths

interface AppPathProvider {

    val userHome: Path

    val pasteAppPath: Path

    val pasteAppJarPath: Path

    val pasteAppExePath: Path

    val pasteUserPath: Path
}

object DesktopAppPathProvider : AppPathProvider, PathProvider {

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

    private fun getAppPathProvider(): AppPathProvider {
        return if (appEnvUtils.isDevelopment()) {
            DevelopmentAppPathProvider()
        } else if (appEnvUtils.isTest()) {
            // In the test environment, DesktopAppPathProvider will be mocked
            this
        } else {
            val platform = getPlatform()
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
}

class DevelopmentAppPathProvider : AppPathProvider {

    private val systemProperty = getSystemProperty()

    private val composeAppDir = systemProperty.get("user.dir")

    private val development = DesktopResourceUtils.loadProperties("development.properties")

    override val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    override val pasteAppPath: Path = getAppPath()

    override val pasteAppJarPath: Path = getResources()

    override val pasteAppExePath: Path = getResources()

    override val pasteUserPath: Path = getUserPath()

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

    private fun getResources(): Path {
        val resources = composeAppDir.toPath().resolve("resources")
        val platform = getPlatform()
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
}

class WindowsAppPathProvider : AppPathProvider {

    private val systemProperty = getSystemProperty()

    override val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    override val pasteAppPath: Path = getAppJarPath().noOptionParent.normalized()

    override val pasteAppJarPath: Path = getAppJarPath()

    override val pasteAppExePath: Path = getAppExePath()

    override val pasteUserPath: Path = getUserPath()

    private fun getAppJarPath(): Path {
        systemProperty.getOption("compose.application.resources.dir")?.let {
            return it.toPath()
        }
        systemProperty.getOption("skiko.library.path")?.let {
            return it.toPath()
        }
        throw IllegalStateException("Could not find app path")
    }

    private fun getAppExePath(): Path {
        return getAppJarPath().noOptionParent.resolve("bin").normalized()
    }

    private fun getUserPath(): Path {
        return userHome.resolve(".crosspaste")
    }
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

    private fun getAppJarPath(): Path {
        systemProperty.getOption("compose.application.resources.dir")?.let {
            return it.toPath()
        }
        systemProperty.getOption("skiko.library.path")?.let {
            return it.toPath()
        }
        throw IllegalStateException("Could not find app path")
    }

    private fun getAppExePath(): Path {
        return getAppJarPath().noOptionParent
            .resolve("runtime")
            .resolve("Contents")
            .resolve("Home")
            .resolve("lib")
            .normalized()
    }

    private fun getUserPath(): Path {
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

class LinuxAppPathProvider : AppPathProvider {

    private val systemProperty = getSystemProperty()

    override val userHome: Path = Paths.get(systemProperty.get("user.home")).toOkioPath()

    override val pasteAppPath: Path = getAppJarPath().noOptionParent.noOptionParent.normalized()

    override val pasteAppJarPath: Path = getAppJarPath()

    override val pasteAppExePath: Path = getAppExePath()

    override val pasteUserPath: Path = getUserPath()

    private fun getAppJarPath(): Path {
        systemProperty.getOption("compose.application.resources.dir")?.let {
            return it.toPath()
        }
        systemProperty.getOption("skiko.library.path")?.let {
            return it.toPath()
        }
        throw IllegalStateException("Could not find app path")
    }

    private fun getAppExePath(): Path {
        return getAppJarPath().noOptionParent.resolve("runtime").resolve("lib").normalized()
    }

    private fun getUserPath(): Path {
        return userHome.resolve(".local").resolve("shard").resolve(".crosspaste")
    }
}
