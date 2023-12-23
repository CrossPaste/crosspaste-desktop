package com.clipevery.dto.model

import com.clipevery.app.AppInfo
import com.clipevery.endpoint.ExplicitEndpointInfo

data class SyncInfo(
    val appInfo: AppInfo,
    val endpointInfo: ExplicitEndpointInfo,
    val state: SyncState
)
