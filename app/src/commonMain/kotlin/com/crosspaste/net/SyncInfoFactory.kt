package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.sync.SyncInfo

class SyncInfoFactory(
    val appInfo: AppInfo,
    private val endpointInfoFactory: EndpointInfoFactory,
) {

    fun createSyncInfo(hostInfoList: List<HostInfo>): SyncInfo =
        SyncInfo(
            appInfo = appInfo,
            endpointInfo = endpointInfoFactory.createEndpointInfo(hostInfoList),
        )
}
