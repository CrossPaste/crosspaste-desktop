package com.clipevery.dto.sync

import com.clipevery.app.AppInfo
import com.clipevery.endpoint.EndpointInfo
import com.clipevery.utils.JsonUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class SyncInfo(val appInfo: AppInfo, val endpointInfo: EndpointInfo) {

    override fun toString(): String {
        return JsonUtils.JSON.encodeToString(this)
    }
}
