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

    override fun resolveUser(configName: String): Path {
        return Paths.get(userHomePath).resolve(".clipevery").resolve(configName)
    }

    override fun resolveApp(configName: String): Path {
        return Paths.get(userDir).resolve(configName)
    }
}


class MacosPathProvider: PathProvider {

    private val userHomePath = System.getProperty("user.home")

    private val userDir = System.getProperty("user.dir")

    override fun resolveUser(configName: String): Path {
        return Paths.get(userHomePath).resolve(".clipevery").resolve(configName)
    }

    override fun resolveApp(configName: String): Path {
        return Paths.get(userDir).resolve(configName)
    }
}

class LinuxPathProvider: PathProvider {

    override fun resolveUser(configName: String): Path {
        TODO("Not yet implemented")
    }

    override fun resolveApp(configName: String): Path {
        TODO("Not yet implemented")
    }
}