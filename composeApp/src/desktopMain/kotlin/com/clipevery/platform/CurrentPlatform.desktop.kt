package com.clipevery.platform

import com.clipevery.utils.OnceFunction
import java.io.BufferedReader
import java.io.InputStreamReader

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
        "win" in osName -> Platform(name = "Windows", arch = architecture, bitMode = bitMode, version = getWindowsVersion())
        "mac" in osName -> Platform(name = "Macos", arch = architecture, bitMode = bitMode, version = version)
        "nix" in osName || "nux" in osName || "aix" in osName -> Platform(name = "Linux", arch = architecture, bitMode = bitMode, version = version)
        else -> Platform(name = "Unknown", arch = architecture, bitMode = bitMode, version = version)
    }
}

private fun getWindowsVersion(): String {
    try {
        val process = Runtime.getRuntime().exec("reg query \"HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\" /v DisplayVersion")
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            if (line!!.contains("DisplayVersion")) {
                return line!!.split(" ").last()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "Unknown"
}