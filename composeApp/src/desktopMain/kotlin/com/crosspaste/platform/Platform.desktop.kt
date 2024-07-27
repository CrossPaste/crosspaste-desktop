package com.crosspaste.platform

import com.crosspaste.utils.OnceFunction
import com.crosspaste.utils.getSystemProperty

actual fun currentPlatform(): Platform {
    return OnceFunction { getCurrentPlatform() }.run()
}

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
            WINDOWS -> getWindowsVersion(name, javaOsVersion)
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
