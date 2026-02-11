package com.crosspaste.platform

import com.crosspaste.utils.getSystemProperty
import okio.Path
import okio.Path.Companion.toPath
import java.io.File

fun getDownloadDir(platform: Platform): Path {
    val systemProperty = getSystemProperty()
    val userHome = systemProperty.get("user.home")

    return when {
        platform.isLinux() -> resolveLinuxDownloadDir(userHome)
        else -> userHome.toPath(normalize = true).resolve("Downloads")
    }
}

private fun resolveLinuxDownloadDir(userHome: String): Path {
    val userDirsFile = File("$userHome/.config/user-dirs.dirs")
    if (userDirsFile.exists()) {
        userDirsFile.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("XDG_DOWNLOAD_DIR=")) {
                val value =
                    trimmed
                        .substringAfter("=")
                        .removeSurrounding("\"")
                        .replace("\$HOME", userHome)
                return value.toPath(normalize = true)
            }
        }
    }
    return userHome.toPath(normalize = true).resolve("Downloads")
}
