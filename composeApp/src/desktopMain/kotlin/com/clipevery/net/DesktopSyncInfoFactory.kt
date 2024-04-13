package com.clipevery.net

import com.clipevery.app.AppInfo
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.endpoint.EndpointInfoFactory

class DesktopSyncInfoFactory(
    val appInfo: AppInfo,
    private val endpointInfoFactory: EndpointInfoFactory,
) : SyncInfoFactory {
    override fun createSyncInfo(): SyncInfo {
        return SyncInfo(
            appInfo = appInfo,
            endpointInfo = endpointInfoFactory.createEndpointInfo(),
        )
    }
}
