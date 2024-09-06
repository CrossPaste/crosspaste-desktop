package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.dao.sync.HostInfo
import com.crosspaste.dto.sync.SyncInfo

class DesktopSyncInfoFactory(
    val appInfo: AppInfo,
    private val endpointInfoFactory: EndpointInfoFactory,
) : SyncInfoFactory {
    override fun createSyncInfo(hostInfoFilter: (HostInfo) -> Boolean): SyncInfo {
        // todo add cache, createEndpointInfo maybe slow
        return SyncInfo(
            appInfo = appInfo,
            endpointInfo =
                endpointInfoFactory.createEndpointInfo(
                    hostInfoFilter,
                ),
        )
    }
}
