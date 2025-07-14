package com.crosspaste.dto.sync

import com.crosspaste.app.AppInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SyncInfo(
    val appInfo: AppInfo,
    val endpointInfo: EndpointInfo,
) {

    override fun toString(): String = Json.encodeToString(this)
}
