package com.clipevery.dto.sync

import com.clipevery.app.AppInfo
import com.clipevery.endpoint.EndpointInfo
import com.clipevery.net.HostInfo

data class SyncInfoUI(var appInfo: AppInfo,
                      var endpointInfo: EndpointInfo,
                      var connectHostInfo: HostInfo?,
                      var syncState: SyncState)

fun SyncInfo.toSyncInfoUI(): SyncInfoUI {
    return SyncInfoUI(appInfo, endpointInfo, null, SyncState.UNKNOWN)
}