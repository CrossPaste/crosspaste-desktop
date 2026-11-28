package com.crosspaste.app

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.net.Server
import com.crosspaste.platform.Platform
import com.crosspaste.utils.DeviceUtils
import kotlinx.coroutines.flow.first

class EndpointInfoFactory(
    private val deviceUtils: DeviceUtils,
    private val pasteServer: Lazy<Server>,
    private val platform: Platform,
) {
    private val deviceName: String by lazy { deviceUtils.getDeviceName() }

    private val deviceId: String by lazy { deviceUtils.getDeviceId() }

    suspend fun createEndpointInfo(hostInfoList: List<HostInfo>): EndpointInfo {
        val port = pasteServer.value.portFlow.first { it > 0 }
        return buildEndpointInfo(hostInfoList, port)
    }

    /**
     * Non-blocking variant of [createEndpointInfo]. Returns null when the local
     * server is not yet listening (`port <= 0`), avoiding indefinite suspension
     * on callers that hold per-device locks (such as outbound heartbeats).
     */
    fun createEndpointInfoIfReady(hostInfoList: List<HostInfo>): EndpointInfo? {
        val port = pasteServer.value.portFlow.value
        if (port <= 0) return null
        return buildEndpointInfo(hostInfoList, port)
    }

    private fun buildEndpointInfo(
        hostInfoList: List<HostInfo>,
        port: Int,
    ): EndpointInfo =
        EndpointInfo(
            deviceId = deviceId,
            deviceName = deviceName,
            platform = platform,
            hostInfoList = hostInfoList,
            port = port,
        )
}
