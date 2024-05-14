package com.clipevery.endpoint

import com.clipevery.net.ClipServer
import com.clipevery.os.macos.api.MacosApi
import com.clipevery.platform.Platform
import com.clipevery.platform.currentPlatform
import com.clipevery.utils.DesktopNetUtils
import com.sun.jna.platform.win32.Kernel32Util
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class DesktopEndpointInfoFactory(private val clipServer: Lazy<ClipServer>) : EndpointInfoFactory {
    override fun createEndpointInfo(): EndpointInfo {
        val platform = currentPlatform()
        val port = clipServer.value.port()
        return if (platform.isMacos()) {
            getMacEndpointInfo(port, platform)
        } else if (platform.isWindows()) {
            getWindowEndpointInfo(port, platform)
        } else if (platform.isLinux()) {
            getLinuxEndpointInfo(port, platform)
        } else {
            throw IllegalStateException("Unsupported platform: $platform")
        }
    }
}

private fun getMacEndpointInfo(
    port: Int,
    platform: Platform,
): EndpointInfo {
    val deviceName = MacosApi.INSTANCE.getComputerName() ?: "Unknown"
    val deviceId = MacosApi.INSTANCE.getHardwareUUID() ?: "Unknown"
    return EndpointInfo(
        deviceId = deviceId,
        deviceName = deviceName,
        platform = platform,
        hostInfoList = DesktopNetUtils.getHostInfoList(),
        port = port,
    )
}

private fun getWindowEndpointInfo(
    port: Int,
    platform: Platform,
): EndpointInfo {
    val deviceName = Kernel32Util.getComputerName()
    val deviceId = getWindowDeviceId()
    return EndpointInfo(
        deviceId = deviceId,
        deviceName = deviceName,
        platform = platform,
        hostInfoList = DesktopNetUtils.getHostInfoList(),
        port = port,
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

fun getLinuxEndpointInfo(
    port: Int,
    platform: Platform,
): EndpointInfo {
    TODO("Not yet implemented")
}
