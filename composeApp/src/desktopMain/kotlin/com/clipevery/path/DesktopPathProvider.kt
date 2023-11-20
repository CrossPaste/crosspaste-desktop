package com.clipevery.path

import com.clipevery.platform.currentPlatform
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun getPathProvider(): PathProvider {
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


class WindowsPathProvider: PathProvider {

    private val userHomePath = System.getProperty("user.home")

    private val userDir = System.getProperty("user.dir")

    override fun resolveUser(fileName: String?): Path {
        return fileName?.let {
            Paths.get(userHomePath).resolve(".clipevery").resolve(fileName)
        } ?: Paths.get(userHomePath).resolve(".clipevery")
    }

    override fun resolveApp(fileName: String?): Path {
        return fileName?.let {
            Paths.get(userDir).resolve(fileName)
        } ?: Paths.get(userDir)
    }

    override fun resolveLog(fileName: String?): Path {
        return fileName?.let {
            Paths.get(userDir).resolve("logs").resolve(fileName)
        } ?: Paths.get(userDir).resolve("logs")
    }
}


class MacosPathProvider: PathProvider {

    private val appPath = System.getProperty("jpackage.app-path")

    private fun getAppSupportPath(): Path {
        val userHome = System.getProperty("user.home")
        val appSupportPath = Paths.get(userHome, "Library", "Application Support", "Clipevery")

        if (Files.notExists(appSupportPath)) {
            Files.createDirectories(appSupportPath)
        }

        return appSupportPath
    }

    override fun resolveUser(fileName: String?): Path {
        return fileName?.let {
            getAppSupportPath().resolve(fileName)
        } ?: getAppSupportPath()
    }

    override fun resolveApp(fileName: String?): Path {
        val appPath = Paths.get(appPath).parent.parent

        return fileName?.let {
            appPath.resolve(fileName)
        } ?: appPath
    }

    override fun resolveLog(fileName: String?): Path {
        return fileName?.let {
            getAppSupportPath().resolve("logs").resolve(fileName)
        } ?: getAppSupportPath().resolve("logs")
    }
}

class LinuxPathProvider: PathProvider {

    override fun resolveUser(fileName: String?): Path {
        TODO("Not yet implemented")
    }

    override fun resolveApp(fileName: String?): Path {
        TODO("Not yet implemented")
    }

    override fun resolveLog(fileName: String?): Path {
        TODO("Not yet implemented")
    }
}