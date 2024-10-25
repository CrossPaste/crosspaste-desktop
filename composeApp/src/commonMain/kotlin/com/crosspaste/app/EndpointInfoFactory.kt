package com.crosspaste.app

import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.net.PasteServer
import com.crosspaste.platform.getPlatform
import com.crosspaste.realm.sync.HostInfo
import com.crosspaste.utils.DeviceUtils
import com.crosspaste.utils.getNetUtils

class EndpointInfoFactory(
    private val deviceUtils: DeviceUtils,
    private val pasteServer: Lazy<PasteServer<*, *>>,
) {
    private val netUtils = getNetUtils()

    fun createEndpointInfo(hostInfoFilter: (HostInfo) -> Boolean = { true }): EndpointInfo {
        val platform = getPlatform()
        val port = pasteServer.value.port()
        val hostInfoList = netUtils.getHostInfoList(hostInfoFilter)
        val deviceName = deviceUtils.getDeviceName()
        val deviceId = deviceUtils.getDeviceId()
        return EndpointInfo(
            deviceId = deviceId,
            deviceName = deviceName,
            platform = platform,
            hostInfoList = hostInfoList,
            port = port,
        )
    }
}
