package com.crosspaste.platform.linux

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

object LinuxPlatform {

    private val logger = KotlinLogging.logger {}

    fun getOsVersion(): String {
        val osName = System.getProperty("os.name")
        if (!osName.startsWith("Linux")) {
            logger.warn { "Not a Linux system" }
            return "Unknown"
        }

        return runCatching {
            when {
                commandExists() -> getLsbReleaseInfo()
                File("/etc/os-release").exists() -> getOsReleaseInfo()
                File("/etc/debian_version").exists() -> getDebianVersion()
                File("/etc/redhat-release").exists() -> getRedHatVersion()
                else -> getGenericLinuxInfo()
            }
        }.onFailure { e ->
            logger.warn(e) { "Unable to determine Linux version" }
        }.getOrElse {
            "Unknown"
        }
    }

    private fun commandExists(): Boolean =
        runCatching {
            ProcessBuilder("which", "lsb_release").start().waitFor() == 0
        }.getOrElse { false }

    private fun getLsbReleaseInfo(): String {
        val process = ProcessBuilder("lsb_release", "-ds").start()
        val output =
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
        return parseOsInfo(output)
    }

    private fun getOsReleaseInfo(): String {
        val content = File("/etc/os-release").readText()
        val name =
            content
                .lineSequence()
                .find { it.startsWith("NAME=") }
                ?.substringAfter("NAME=")
                ?.trim('"')
                ?: "Unknown"
        val version =
            content
                .lineSequence()
                .find { it.startsWith("VERSION_ID=") }
                ?.substringAfter("VERSION_ID=")
                ?.trim('"')
                ?: "Unknown"
        return "$name $version"
    }

    private fun getDebianVersion(): String {
        val version = File("/etc/debian_version").readText().trim()
        return "Debian $version"
    }

    private fun getRedHatVersion(): String = parseOsInfo(File("/etc/redhat-release").readText().trim())

    private fun getGenericLinuxInfo(): String {
        val kernelVersion = System.getProperty("os.version")
        return "Linux (Kernel $kernelVersion)"
    }

    private fun parseOsInfo(info: String): String {
        val parts = info.split(" ")
        val name = parts.firstOrNull() ?: "Unknown"
        val version = parts.lastOrNull { it.matches(Regex("\\d+(\\.\\d+)*")) } ?: "Unknown"
        return "$name $version"
    }
}
