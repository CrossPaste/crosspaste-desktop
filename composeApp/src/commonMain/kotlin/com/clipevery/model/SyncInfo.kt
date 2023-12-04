package com.clipevery.model

data class SyncInfo(
    val appInfo: AppInfo,
    val endpointInfo: EndpointInfo,
    val state: SyncState
)
