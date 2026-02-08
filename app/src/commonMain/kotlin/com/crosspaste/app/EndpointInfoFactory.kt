package com.crosspaste.app

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.net.Server
import com.crosspaste.platform.Platform
import com.crosspaste.utils.DeviceUtils
import kotlinx.coroutines.flow.first

class EndpointInfoFactory(
    deviceUtils: DeviceUtils,
    private val pasteServer: Lazy<Server>,
    private val platform: Platform,
) {
    private val deviceName = deviceUtils.getDeviceName()

    private val deviceId = deviceUtils.getDeviceId()

    suspend fun createEndpointInfo(hostInfoList: List<HostInfo>): EndpointInfo {
        val port = pasteServer.value.portFlow.first { it > 0 }
        return EndpointInfo(
            deviceId = deviceId,
            deviceName = deviceName,
            platform = platform,
            hostInfoList = hostInfoList,
            port = port,
        )
    }
}
