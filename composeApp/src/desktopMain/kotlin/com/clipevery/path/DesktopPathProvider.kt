package com.clipevery.path

import com.clipevery.platform.currentPlatform
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