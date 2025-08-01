package com.crosspaste.dto.sync

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.platform.Platform
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EndpointInfo(
    val deviceId: String,
    val deviceName: String,
    val platform: Platform,
    val hostInfoList: List<HostInfo>,
    val port: Int,
) {
    override fun toString(): String = Json.encodeToString(this)
}
