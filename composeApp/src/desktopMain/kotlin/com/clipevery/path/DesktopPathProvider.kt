package com.clipevery.path

import com.clipevery.app.AppEnv
import com.clipevery.platform.currentPlatform
import com.clipevery.utils.ResourceUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object DesktopPathProvider: PathProvider {

    private val pathProvider = getPathProvider()

    override val clipAppPath: Path = pathProvider.clipAppPath

    override val clipUserPath: Path = pathProvider.clipUserPath

    override val clipLogPath: Path get() = pathProvider.clipUserPath

    override val clipEncryptPath get() = pathProvider.clipUserPath

    override val clipDataPath get() = pathProvider.clipUserPath

    private fun getPathProvider(): PathProvider {
        val appEnv = AppEnv.getAppEnv()
        return if (appEnv == AppEnv.DEVELOPMENT) {
            DevelopmentPathProvider()
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

class DevelopmentPathProvider:PathProvider {

    private val composeAppDir = System.getProperty("user.dir")

    private val development = ResourceUtils.loadProperties("development.properties")

    override val clipAppPath: Path = getAppPath()

    override val clipUserPath: Path = getUserPath()

    private fun getAppPath(): Path {
        development.getProperty("clipAppPath")?.let {
            val path = Paths.get(it)
            if (path.isAbsolute) {
                return path
            } else {
                return Paths.get(composeAppDir).resolve(it)
            }
        } ?: run {
            return Paths.get(composeAppDir)
        }
    }

    private fun getUserPath(): Path {
        development.getProperty("clipUserPath")?.let {
            val path = Paths.get(it)
            if (path.isAbsolute) {
                return path
            } else {
                return Paths.get(composeAppDir).resolve(it)
            }
        } ?: run {
            return Paths.get(composeAppDir).resolve(".user")
        }
    }

}

class WindowsPathProvider: PathProvider {

    private val userHomePath = System.getProperty("user.home")

    override val clipAppPath: Path = getAppPath()

    override val clipUserPath: Path = Paths.get(userHomePath).resolve(".clipevery")

    private fun getAppPath(): Path {
        System.getProperty("compose.application.resources.dir")?.let {
            return Paths.get(it).parent
        }
        System.getProperty("skiko.library.path")?.let {
            return Paths.get(it)
        }
        throw IllegalStateException("Could not find app path")
    }
}


class MacosPathProvider: PathProvider {

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

    private val userHome = System.getProperty("user.home")

    override val clipAppPath: Path = getAppPath()

    override val clipUserPath: Path = getAppSupportPath()

    override val clipLogPath: Path = getLogPath()

    private fun getAppSupportPath(): Path {
        val appSupportPath = Paths.get(userHome, "Library", "Application Support", "Clipevery")

        if (Files.notExists(appSupportPath)) {
            Files.createDirectories(appSupportPath)
        }

        return appSupportPath
    }

    private fun getLogPath(): Path {
        val appLogsPath = Paths.get(userHome, "Library", "Logs", "Clipevery")

        if (Files.notExists(appLogsPath)) {
            Files.createDirectories(appLogsPath)
        }

        return appLogsPath
    }

    private fun getAppPath(): Path {
        System.getProperty("compose.application.resources.dir")?.let {
            return Paths.get(it).parent
        }
        System.getProperty("skiko.library.path")?.let {
            return Paths.get(it)
        }
        throw IllegalStateException("Could not find app path")
    }
}

class LinuxPathProvider: PathProvider {
    override val clipAppPath: Path
        get() = TODO("Not yet implemented")
    override val clipUserPath: Path
        get() = TODO("Not yet implemented")
}