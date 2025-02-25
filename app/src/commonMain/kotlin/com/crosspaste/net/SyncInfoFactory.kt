package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.utils.HostInfoFilter

class SyncInfoFactory(
    val appInfo: AppInfo,
    private val endpointInfoFactory: EndpointInfoFactory,
) {

    fun createSyncInfo(hostInfoFilter: HostInfoFilter): SyncInfo {
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
