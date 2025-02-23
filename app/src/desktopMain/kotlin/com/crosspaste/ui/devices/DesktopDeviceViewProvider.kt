package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.dto.sync.SyncInfo

class DesktopDeviceViewProvider : DeviceViewProvider {

    @Composable
    override fun DeviceConnectView(
        syncRuntimeInfo: SyncRuntimeInfo,
        deviceInteractionEnabled: Boolean,
        onEdit: (SyncRuntimeInfo) -> Unit,
    ) {
        DeviceConnectContentView(syncRuntimeInfo, deviceInteractionEnabled, onEdit)
    }

    @Composable
    override fun SyncDeviceView(
        syncInfo: SyncInfo,
        action: @Composable ((Color) -> Unit),
    ) {
        SyncDeviceContentView(syncInfo, action)
    }
}
