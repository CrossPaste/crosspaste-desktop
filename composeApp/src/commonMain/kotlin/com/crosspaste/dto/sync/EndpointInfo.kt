package com.crosspaste.dto.sync

import com.crosspaste.platform.Platform
import com.crosspaste.realm.sync.HostInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class EndpointInfo(
    val deviceId: String,
    val deviceName: String,
    val platform: Platform,
    val hostInfoList: List<HostInfo>,
    val port: Int,
) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}
