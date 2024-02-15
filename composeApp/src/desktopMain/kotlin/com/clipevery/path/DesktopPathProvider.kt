package com.clipevery.path

import com.clipevery.platform.currentPlatform
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object DesktopPathProvider: PathProvider {

    private val pathProvider = getPathProvider()

    override val clipUserPath: Path = pathProvider.clipUserPath

    override val clipLogPath: Path get() = pathProvider.clipUserPath

    override val clipEncryptPath get() = pathProvider.clipUserPath

    override val clipDataPath get() = pathProvider.clipUserPath

    private fun getPathProvider(): PathProvider {
        return if (currentPlatform().isWindows()) {
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

class WindowsPathProvider: PathProvider {

    private val userHomePath = System.getProperty("user.home")

    override val clipUserPath: Path = Paths.get(userHomePath).resolve(".clipevery")
}


class MacosPathProvider: PathProvider {

    private val userHome = System.getProperty("user.home")

    override val clipUserPath: Path = getAppSupportPath()

    private fun getAppSupportPath(): Path {
        val appSupportPath = Paths.get(userHome, "Library", "Application Support", "Clipevery")

        if (Files.notExists(appSupportPath)) {
            Files.createDirectories(appSupportPath)
        }

        return appSupportPath
    }

    override val clipLogPath: Path
        get() = run {
            val appLogsPath = Paths.get(userHome, "Library", "Logs", "Clipevery")

            if (Files.notExists(appLogsPath)) {
                Files.createDirectories(appLogsPath)
            }

            return appLogsPath
        }
}

class LinuxPathProvider: PathProvider {
    override val clipUserPath: Path
        get() = TODO("Not yet implemented")
}