package com.crosspaste.utils

import com.crosspaste.os.macos.MacDeviceUtils
import com.crosspaste.platform.currentPlatform
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID

actual fun getDeviceUtils(): DeviceUtils {
    return DesktopDeviceUtils
}

object DesktopDeviceUtils : DeviceUtils {

    override fun createAppInstanceId(): String {
        return if (currentPlatform().isWindows()) {
            WindowsDeviceUtils.createAppInstanceId()
        } else if (currentPlatform().isMacos()) {
            MacosDeviceUtils.createAppInstanceId()
        } else if (currentPlatform().isLinux()) {
            LinuxDeviceUtils.createAppInstanceId()
        } else {
            throw IllegalStateException("Unknown platform: ${currentPlatform().name}")
        }
    }
}

object WindowsDeviceUtils : DeviceUtils {

    override fun createAppInstanceId(): String {
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
        return UUID.randomUUID().toString()
    }
}

object MacosDeviceUtils : DeviceUtils {

    override fun createAppInstanceId(): String {
        return MacDeviceUtils.getHardwareUUID() ?: UUID.randomUUID().toString()
    }
}

object LinuxDeviceUtils : DeviceUtils {

    override fun createAppInstanceId(): String {
        val file = File("/etc/machine-id")
        return if (file.exists()) {
            file.readText().trim()
        } else {
            UUID.randomUUID().toString()
        }
    }
}
