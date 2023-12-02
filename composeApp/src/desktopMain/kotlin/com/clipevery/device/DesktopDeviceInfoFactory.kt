package com.clipevery.device

import com.clipevery.macos.api.MacosApi
import com.clipevery.model.DeviceInfo
import com.clipevery.model.HostInfo
import com.clipevery.platform.Platform
import com.clipevery.platform.currentPlatform
import com.sun.jna.platform.win32.Kernel32Util
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.util.Collections

class DesktopDeviceInfoFactory: DeviceInfoFactory {
    override fun createDeviceInfo(): DeviceInfo {
        val platform = currentPlatform()
        return if (platform.isMacos()) {
            getMacDeviceInfo(platform)
        } else if (platform.isWindows()) {
            getWindowDeviceInfo(platform)
        } else {
            throw IllegalStateException("Unsupported platform: $platform")
        }
    }
}

private fun getMacDeviceInfo(platform: Platform): DeviceInfo {
    val deviceName = MacosApi.INSTANCE.getComputerName() ?: "Unknown"
    val deviceId = MacosApi.INSTANCE.getHardwareUUID() ?: "Unknown"
    return DeviceInfo(
        deviceId = deviceId,
        deviceName = deviceName,
        platform = platform,
        hostInfoList = getHostInfoList()
    )
}

private fun getWindowDeviceInfo(platform: Platform): DeviceInfo {
    val deviceName = Kernel32Util.getComputerName()
    val deviceId = getWindowDeviceId()
    return DeviceInfo(
        deviceId = deviceId,
        deviceName = deviceName,
        platform = platform,
        hostInfoList = getHostInfoList()
    )
}

fun getWindowDeviceId(): String {
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
    return "Unknown"
}


private fun getHostInfoList(): List<HostInfo> {
    val nets = NetworkInterface.getNetworkInterfaces()

    return buildList {
        for (netInterface in Collections.list(nets)) {
            val inetAddresses = netInterface.inetAddresses
            for (inetAddress in Collections.list(inetAddresses)) {
                if (inetAddress.isSiteLocalAddress) {
                    add(
                        HostInfo(displayName = netInterface.displayName,
                            hostAddress = inetAddress.hostAddress)
                    )
                }
            }
        }
    }
}