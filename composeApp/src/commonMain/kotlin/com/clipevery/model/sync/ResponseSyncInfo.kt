package com.clipevery.model.sync

import com.clipevery.model.AppInfo
import com.clipevery.model.RequestEndpointInfo

data class ResponseSyncInfo(val appInfo: AppInfo,
                            val requestEndpointInfo: RequestEndpointInfo
)
