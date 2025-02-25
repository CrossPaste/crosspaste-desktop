package com.crosspaste.app

import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.net.Server
import com.crosspaste.platform.getPlatform
import com.crosspaste.utils.DeviceUtils
import com.crosspaste.utils.HostInfoFilter
import com.crosspaste.utils.NoFilter
import com.crosspaste.utils.getNetUtils

class EndpointInfoFactory(
    deviceUtils: DeviceUtils,
    private val pasteServer: Lazy<Server>,
) {
    private val netUtils = getNetUtils()

    private val platform = getPlatform()

    private val deviceName = deviceUtils.getDeviceName()

    private val deviceId = deviceUtils.getDeviceId()

    fun createEndpointInfo(hostInfoFilter: HostInfoFilter = NoFilter): EndpointInfo {
        val port = pasteServer.value.port()
        val hostInfoList = netUtils.getHostInfoList(hostInfoFilter)
        return EndpointInfo(
            deviceId = deviceId,
            deviceName = deviceName,
            platform = platform,
            hostInfoList = hostInfoList,
            port = port,
        )
    }
}
