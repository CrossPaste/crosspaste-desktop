package com.crosspaste.dto.sync

import com.crosspaste.app.AppInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SyncInfo(
    val appInfo: AppInfo,
    val endpointInfo: EndpointInfo,
) {
    fun merge(other: SyncInfo): SyncInfo {
        val hostInfoList = endpointInfo.hostInfoList
        val otherHostInfoList = other.endpointInfo.hostInfoList

        val mergedList =
            (hostInfoList + otherHostInfoList)
                .distinctBy { it.hostAddress }

        val appInfo = other.appInfo
        val endpointInfo =
            EndpointInfo(
                deviceId = other.endpointInfo.deviceId,
                deviceName = other.endpointInfo.deviceName,
                platform = other.endpointInfo.platform,
                hostInfoList = mergedList,
                port = other.endpointInfo.port,
            )
        return SyncInfo(appInfo, endpointInfo)
    }

    override fun toString(): String = Json.encodeToString(this)
}
