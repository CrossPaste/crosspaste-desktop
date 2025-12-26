package com.crosspaste.ui.devices

import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform

class DesktopSyncScope(
    override val syncInfo: SyncInfo,
) : SyncScope {

    override val platform: Platform
        get() = syncInfo.endpointInfo.platform

    override fun getDeviceDisplayName(): String = syncInfo.endpointInfo.deviceName
}
