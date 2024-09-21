package com.crosspaste.app

import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.net.PasteServer
import com.crosspaste.platform.getPlatform
import com.crosspaste.realm.sync.HostInfo
import com.crosspaste.utils.getDeviceUtils
import com.crosspaste.utils.getNetUtils

class DesktopEndpointInfoFactory(
    private val pasteServer: Lazy<PasteServer<*, *>>,
) : EndpointInfoFactory {

    private val deviceUtils = getDeviceUtils()

    private val netUtils = getNetUtils()

    override fun createEndpointInfo(hostInfoFilter: (HostInfo) -> Boolean): EndpointInfo {
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
