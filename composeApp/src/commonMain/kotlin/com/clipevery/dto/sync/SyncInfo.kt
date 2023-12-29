package com.clipevery.dto.sync

import com.clipevery.app.AppInfo
import com.clipevery.endpoint.EndpointInfo
import kotlinx.serialization.Serializable

@Serializable
data class SyncInfo(val appInfo: AppInfo, val endpointInfo: EndpointInfo)
