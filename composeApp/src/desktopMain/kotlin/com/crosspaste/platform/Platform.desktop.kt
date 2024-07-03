package com.crosspaste.platform

import com.crosspaste.utils.OnceFunction
import com.crosspaste.utils.getSystemProperty

actual fun currentPlatform(): Platform {
    return OnceFunction { getCurrentPlatform() }.run()
}

private fun getCurrentPlatform(): Platform {
    val systemProperty = getSystemProperty()
    val osName = systemProperty.get("os.name").lowercase()
    val version = systemProperty.get("os.version")
    val architecture = systemProperty.get("os.arch")
    val bitMode =
        if (architecture.contains("64")) {
            64
        } else {
            32
        }
    return when {
        "win" in osName -> Platform(name = "Windows", arch = architecture, bitMode = bitMode, version = getWindowsVersion(osName, version))
        "mac" in osName -> Platform(name = "Macos", arch = architecture, bitMode = bitMode, version = version)
        "nix" in osName || "nux" in osName || "aix" in osName ->
            Platform(
                name = "Linux",
                arch = architecture,
                bitMode = bitMode,
                version = version,
            )
        else -> Platform(name = "Unknown", arch = architecture, bitMode = bitMode, version = version)
    }
}

private fun getWindowsVersion(
    osName: String,
    osVersion: String,
): String {
    val parts = osName.split(" ", limit = 2)
    return if (parts.size > 1) parts[1] else osVersion
}
