package com.clipevery.device

import com.clipevery.macos.api.MacosApi
import com.clipevery.model.DeviceInfo
import com.clipevery.model.HostInfo
import com.clipevery.platform.Platform
import com.clipevery.platform.currentPlatform
import java.net.NetworkInterface
import java.util.Collections

class DesktopDeviceInfoFactory: DeviceInfoFactory {
    override fun createDeviceInfo(): DeviceInfo {
        val platform = currentPlatform()
        return if (platform.isMacos()) {
            getMacDeviceInfo(platform)
        } else if (platform.isWindows()) {
            getWindowDeviceInfo()
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

private fun getWindowDeviceInfo(): DeviceInfo {
    TODO("Not yet implemented")
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