package com.crosspaste.app

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.net.Server
import com.crosspaste.platform.Platform
import com.crosspaste.utils.DeviceUtils

class EndpointInfoFactory(
    deviceUtils: DeviceUtils,
    private val pasteServer: Lazy<Server>,
    private val platform: Platform,
) {
    private val deviceName = deviceUtils.getDeviceName()

    private val deviceId = deviceUtils.getDeviceId()

    fun createEndpointInfo(hostInfoList: List<HostInfo>): EndpointInfo {
        val port = pasteServer.value.port()
        return EndpointInfo(
            deviceId = deviceId,
            deviceName = deviceName,
            platform = platform,
            hostInfoList = hostInfoList,
            port = port,
        )
    }
}
