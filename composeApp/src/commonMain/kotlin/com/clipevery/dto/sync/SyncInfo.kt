package com.clipevery.dto.sync

import com.clipevery.app.AppInfo
import com.clipevery.endpoint.EndpointInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SyncInfo(val appInfo: AppInfo, val endpointInfo: EndpointInfo) {

    override fun toString(): String {
        return Json.encodeToString(this)
    }
}
