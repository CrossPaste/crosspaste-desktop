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

    override val clipUserPath: Path = Paths.get(userHomePath).resolve(".clipevery")
}


class MacosPathProvider: PathProvider {

    override val clipUserPath: Path = getAppSupportPath()

    private fun getAppSupportPath(): Path {
        val userHome = System.getProperty("user.home")
        val appSupportPath = Paths.get(userHome, "Library", "Application Support", "Clipevery")

        if (Files.notExists(appSupportPath)) {
            Files.createDirectories(appSupportPath)
        }

        return appSupportPath
    }

}

class LinuxPathProvider: PathProvider {
    override val clipUserPath: Path
        get() = TODO("Not yet implemented")
}