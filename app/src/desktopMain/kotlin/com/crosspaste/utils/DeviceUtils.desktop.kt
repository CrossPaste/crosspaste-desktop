package com.crosspaste.utils

import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacDeviceUtils
import com.sun.jna.platform.win32.Kernel32Util
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID

class DesktopDeviceUtils(
    platform: Platform,
) : DeviceUtils {

    private val deviceUtils =
        if (platform.isWindows()) {
            WindowsDeviceUtils
        } else if (platform.isMacos()) {
            MacosDeviceUtils
        } else if (platform.isLinux()) {
            LinuxDeviceUtils
        } else {
            throw IllegalStateException("Unknown platform: ${platform.name}")
        }

    override fun createAppInstanceId(): String = deviceUtils.createAppInstanceId()

    override fun getDeviceId(): String = deviceUtils.getDeviceId()

    override fun getDeviceName(): String = deviceUtils.getDeviceName()
}

object WindowsDeviceUtils : DeviceUtils {

    private val logger = KotlinLogging.logger {}

    private fun getProductUUID(): String? {
        runCatching {
            val command = listOf("wmic", "csproduct", "get", "UUID")
            val process = ProcessBuilder(command).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String
            while (reader.readLine().also { line = it } != null) {
                if (line.trim { it <= ' ' }.isNotEmpty() && line.trim { it <= ' ' } != "UUID") {
                    return line.trim { it <= ' ' }
                }
            }
        }.onFailure { e ->
            logger.error(e) { "Failed to get product UUID" }
        }
        return null
    }

    override fun createAppInstanceId(): String = getProductUUID() ?: UUID.randomUUID().toString()

    override fun getDeviceId(): String = getProductUUID() ?: "Unknown"

    override fun getDeviceName(): String = Kernel32Util.getComputerName()
}

object MacosDeviceUtils : DeviceUtils {

    override fun createAppInstanceId(): String = MacDeviceUtils.getHardwareUUID() ?: UUID.randomUUID().toString()

    override fun getDeviceId(): String = MacDeviceUtils.getHardwareUUID() ?: "Unknown"

    override fun getDeviceName(): String = MacDeviceUtils.getComputerName() ?: "Unknown"
}

object LinuxDeviceUtils : DeviceUtils {

    private fun getMachineId(): String? {
        val file = File("/etc/machine-id")
        return if (file.exists()) {
            file.readText().trim()
        } else {
            null
        }
    }

    override fun createAppInstanceId(): String = getMachineId() ?: UUID.randomUUID().toString()

    override fun getDeviceId(): String = getMachineId() ?: "Unknown"

    override fun getDeviceName(): String {
        val process = ProcessBuilder("hostname").start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val hostName = reader.readLine()
        reader.close()
        return hostName
    }
}
