package com.crosspaste.ui.devices

import com.crosspaste.db.sync.SyncRuntimeInfo

interface DeviceScopeFactory {

    fun createDeviceScope(syncRuntimeInfo: SyncRuntimeInfo): DeviceScope
}
