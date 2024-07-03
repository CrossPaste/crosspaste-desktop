package com.crosspaste.endpoint

import com.crosspaste.dao.sync.HostInfo
import com.crosspaste.net.ClipServer
import com.crosspaste.os.macos.api.MacosApi
import com.crosspaste.platform.Platform
import com.crosspaste.platform.currentPlatform
import com.crosspaste.utils.DesktopNetUtils
import com.crosspaste.utils.getDeviceUtils
import com.sun.jna.platform.win32.Kernel32Util
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class DesktopEndpointInfoFactory(private val clipServer: Lazy<ClipServer>) : EndpointInfoFactory {
    override fun createEndpointInfo(hostInfoFilter: (HostInfo) -> Boolean): EndpointInfo {
        val platform = currentPlatform()
        val port = clipServer.value.port()
        return if (platform.isMacos()) {
            getMacEndpointInfo(port, platform, hostInfoFilter)
        } else if (platform.isWindows()) {
            getWindowEndpointInfo(port, platform, hostInfoFilter)
        } else if (platform.isLinux()) {
            getLinuxEndpointInfo(port, platform, hostInfoFilter)
        } else {
            throw IllegalStateException("Unsupported platform: $platform")
        }
    }
}

private fun getMacEndpointInfo(
    port: Int,
    platform: Platform,
    hostInfoFilter: (HostInfo) -> Boolean,
): EndpointInfo {
    val deviceName = MacosApi.INSTANCE.getComputerName() ?: "Unknown"
    val deviceId = MacosApi.INSTANCE.getHardwareUUID() ?: "Unknown"
    return EndpointInfo(
        deviceId = deviceId,
        deviceName = deviceName,
        platform = platform,
        hostInfoList = DesktopNetUtils.getHostInfoList(hostInfoFilter),
        port = port,
    )
}

private fun getWindowEndpointInfo(
    port: Int,
    platform: Platform,
    hostInfoFilter: (HostInfo) -> Boolean,
): EndpointInfo {
    val deviceName = Kernel32Util.getComputerName()
    val deviceId = getWindowDeviceId()
    return EndpointInfo(
        deviceId = deviceId,
        deviceName = deviceName,
        platform = platform,
        hostInfoList = DesktopNetUtils.getHostInfoList(hostInfoFilter),
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
    hostInfoFilter: (HostInfo) -> Boolean,
): EndpointInfo {
    fun getHostName(): String {
        val process = Runtime.getRuntime().exec("hostname")
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val hostName = reader.readLine()
        reader.close()
        return hostName
    }

    val deviceName = getHostName()
    val deviceId = getDeviceUtils().createAppInstanceId()
    return EndpointInfo(
        deviceId = deviceId,
        deviceName = deviceName,
        platform = platform,
        hostInfoList = DesktopNetUtils.getHostInfoList(hostInfoFilter),
        port = port,
    )
}
