package com.crosspaste.utils

import com.crosspaste.platform.getPlatform
import com.crosspaste.platform.macos.MacDeviceUtils
import com.sun.jna.platform.win32.Kernel32Util
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID

actual fun getDeviceUtils(): DeviceUtils {
    return DesktopDeviceUtils
}

object DesktopDeviceUtils : DeviceUtils {

    private val deviceUtils =
        if (getPlatform().isWindows()) {
            WindowsDeviceUtils
        } else if (getPlatform().isMacos()) {
            MacosDeviceUtils
        } else if (getPlatform().isLinux()) {
            LinuxDeviceUtils
        } else {
            throw IllegalStateException("Unknown platform: ${getPlatform().name}")
        }

    override fun createAppInstanceId(): String {
        return deviceUtils.createAppInstanceId()
    }

    override fun getDeviceId(): String {
        return deviceUtils.getDeviceId()
    }

    override fun getDeviceName(): String {
        return deviceUtils.getDeviceName()
    }
}

object WindowsDeviceUtils : DeviceUtils {

    private fun getProductUUID(): String? {
        try {
            val command = "wmic csproduct get UUID"
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String
            while (reader.readLine().also { line = it } != null) {
                if (line.trim { it <= ' ' }.isNotEmpty() && line.trim { it <= ' ' } != "UUID") {
                    return line.trim { it <= ' ' }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    override fun createAppInstanceId(): String {
        return getProductUUID() ?: UUID.randomUUID().toString()
    }

    override fun getDeviceId(): String {
        return getProductUUID() ?: "Unknown"
    }

    override fun getDeviceName(): String {
        return Kernel32Util.getComputerName()
    }
}

object MacosDeviceUtils : DeviceUtils {

    override fun createAppInstanceId(): String {
        return MacDeviceUtils.getHardwareUUID() ?: UUID.randomUUID().toString()
    }

    override fun getDeviceId(): String {
        return MacDeviceUtils.getHardwareUUID() ?: "Unknown"
    }

    override fun getDeviceName(): String {
        return MacDeviceUtils.getComputerName() ?: "Unknown"
    }
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

    override fun createAppInstanceId(): String {
        return getMachineId() ?: UUID.randomUUID().toString()
    }

    override fun getDeviceId(): String {
        return getMachineId() ?: "Unknown"
    }

    override fun getDeviceName(): String {
        val process = Runtime.getRuntime().exec("hostname")
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val hostName = reader.readLine()
        reader.close()
        return hostName
    }
}
