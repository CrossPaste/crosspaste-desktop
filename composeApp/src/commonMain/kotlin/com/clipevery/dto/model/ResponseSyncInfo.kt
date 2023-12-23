package com.clipevery.dto.model

import com.clipevery.app.AppInfo
import com.clipevery.endpoint.EndpointInfo
import kotlinx.serialization.Serializable

@Serializable
data class ResponseSyncInfo(val appInfo: AppInfo,
                            val endpointInfo: EndpointInfo
)
