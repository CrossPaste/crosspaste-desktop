package com.clipevery.platform

actual fun currentPlatform(): Platform {
    val osName = System.getProperty("os.name").lowercase()
    val version = System.getProperty("os.version")
    return when {
        "win" in osName -> object : Platform {
            override val name = "Windows"
            override val version = version
        }
        "mac" in osName -> object : Platform {
            override val name = "Macos"
            override val version = version
        }
        "nix" in osName || "nux" in osName || "aix" in osName -> object : Platform {
            override val name = "Linux"
            override val version = version
        }
        else -> object : Platform {
            override val name = "Unknown"
            override val version = version
        }
    }
}