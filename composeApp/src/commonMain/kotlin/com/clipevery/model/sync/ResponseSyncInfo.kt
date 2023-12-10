package com.clipevery.model.sync

import com.clipevery.app.AppInfo
import com.clipevery.endpoint.EndpointInfo

data class ResponseSyncInfo(val appInfo: AppInfo,
                            val endpointInfo: EndpointInfo
)
