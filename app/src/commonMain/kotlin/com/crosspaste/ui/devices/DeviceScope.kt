package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.platform.Platform

interface DeviceScope : PlatformScope {

    var syncRuntimeInfo: SyncRuntimeInfo

    override val platform: Platform
        get() = syncRuntimeInfo.platform

    override fun getDeviceDisplayName(): String = syncRuntimeInfo.getDeviceDisplayName()

    @Composable
    fun DeviceConnectView()
}
