package com.crosspaste.platform

import com.crosspaste.platform.Platform.Companion.LINUX
import com.crosspaste.platform.Platform.Companion.MACOS
import com.crosspaste.platform.Platform.Companion.UNKNOWN_OS
import com.crosspaste.platform.Platform.Companion.WINDOWS
import com.crosspaste.platform.linux.LinuxPlatform
import com.crosspaste.utils.getSystemProperty

class DesktopPlatformProvider : PlatformProvider {
    override fun getPlatform(): Platform = getCurrentPlatform()

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
}
