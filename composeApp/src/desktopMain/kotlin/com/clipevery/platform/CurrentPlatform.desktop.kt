package com.clipevery.platform

import com.clipevery.utils.OnceFunction

actual fun currentPlatform(): Platform {
    return OnceFunction { getCurrentPlatform() }.run()
}

private fun getCurrentPlatform(): Platform {
    val osName = System.getProperty("os.name").lowercase()
    val version = System.getProperty("os.version")
    val architecture = System.getProperty("os.arch")
    val bitMode = if (architecture.contains("64")) {
        64
    } else {
        32
    }
    return when {
        "win" in osName -> Platform(name = "Windows", arch = architecture, bitMode = bitMode, version = version)
        "mac" in osName -> Platform(name = "Macos", arch = architecture, bitMode = bitMode, version = version)
        "nix" in osName || "nux" in osName || "aix" in osName -> Platform(name = "Linux", arch = architecture, bitMode = bitMode, version = version)
        else -> Platform(name = "Unknown", arch = architecture, bitMode = bitMode, version = version)
    }
}