package com.crosspaste.ui.devices

import com.crosspaste.db.sync.SyncRuntimeInfo

class DesktopDeviceScopeFactory : DeviceScopeFactory {
    override fun createDeviceScope(syncRuntimeInfo: SyncRuntimeInfo): DeviceScope = DesktopDeviceScope(syncRuntimeInfo)
}
