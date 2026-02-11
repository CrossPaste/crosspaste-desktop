package com.crosspaste.utils

import com.crosspaste.platform.LinuxPlatform
import com.crosspaste.platform.Platform
import com.crosspaste.platform.Platform.Companion.LINUX
import com.crosspaste.platform.Platform.Companion.MACOS
import com.crosspaste.platform.Platform.Companion.UNKNOWN_OS
import com.crosspaste.platform.Platform.Companion.WINDOWS
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer

actual fun getPlatformUtils(): PlatformUtils = DesktopPlatformUtils

object DesktopPlatformUtils : PlatformUtils {

    override val platform: Platform by lazy { getCurrentPlatform() }

    private fun getCurrentPlatform(): Platform {
        val systemProperty = getSystemProperty()
        val osName = systemProperty.get("os.name").lowercase()

        val name =
            when {
                "win" in osName -> WINDOWS
                "mac" in osName -> MACOS
                "nix" in osName || "nux" in osName || "aix" in osName -> LINUX
                else -> UNKNOWN_OS
            }

        val javaOsVersion = systemProperty.get("os.version")

        val version =
            when (name) {
                WINDOWS -> getWindowsVersion(osName, javaOsVersion)
                MACOS -> javaOsVersion
                LINUX -> LinuxPlatform.getOsVersion()
                else -> javaOsVersion
            }
        val architecture = systemProperty.get("os.arch")
        val bitMode =
            if (architecture.contains("64")) {
                64
            } else {
                32
            }

        return Platform(name = name, arch = architecture, bitMode = bitMode, version = version)
    }

    private fun getWindowsVersion(
        osName: String,
        javaOsVersion: String,
    ): String {
        val parts = osName.split(" ", limit = 2)
        return if (parts.size > 1) parts[1] else javaOsVersion
    }

    override fun getSystemDownloadDir(): Path {
        val userHome = getSystemProperty().get("user.home")
        return if (platform.isLinux()) {
            resolveLinuxDownloadDir(userHome)
        } else {
            userHome.toPath(normalize = true) / "Downloads"
        }
    }

    private fun resolveLinuxDownloadDir(userHome: String): Path {
        val userHomePath = userHome.toPath(normalize = true)
        val userDirsFile = userHomePath / ".config" / "user-dirs.dirs"
        if (FileSystem.SYSTEM.exists(userDirsFile)) {
            FileSystem.SYSTEM.source(userDirsFile).buffer().use { source ->
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.startsWith("XDG_DOWNLOAD_DIR=")) {
                        val value =
                            trimmed
                                .substringAfter("=")
                                .removeSurrounding("\"")
                                .replace($$"$HOME", userHome)
                                .replace($$"${HOME}", userHome)
                        return value.toPath(normalize = true)
                    }
                }
            }
        }
        return userHomePath / "Downloads"
    }
}
