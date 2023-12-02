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
        "win" in osName -> object : Platform {
            override val name = "Windows"
            override val bitMode: Int = bitMode
            override val arch: String = architecture
            override val version = version
        }
        "mac" in osName -> object : Platform {
            override val name = "Macos"
            override val arch: String = architecture
            override val bitMode: Int = bitMode
            override val version = version
        }
        "nix" in osName || "nux" in osName || "aix" in osName -> object : Platform {
            override val name = "Linux"
            override val arch: String = architecture
            override val bitMode: Int = bitMode
            override val version = version
        }
        else -> object : Platform {
            override val name = "Unknown"
            override val arch: String = architecture
            override val bitMode: Int = bitMode
            override val version = version
        }
    }
}