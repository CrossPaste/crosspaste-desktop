package com.clipevery.utils

import com.clipevery.os.macos.api.MacosApi
import com.clipevery.platform.currentPlatform
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID

object DesktopDeviceUtils: DeviceUtils {

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

object WindowsDeviceUtils: DeviceUtils {

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

object MacosDeviceUtils: DeviceUtils {

    override fun createAppInstanceId(): String {
        return MacosApi.INSTANCE.getHardwareUUID() ?: UUID.randomUUID().toString()
    }
}

object LinuxDeviceUtils: DeviceUtils {

    override fun createAppInstanceId(): String {
        return UUID.randomUUID().toString()
    }
}

