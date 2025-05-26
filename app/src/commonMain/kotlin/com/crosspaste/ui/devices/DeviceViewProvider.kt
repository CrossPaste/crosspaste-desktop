package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.dto.sync.SyncInfo

interface DeviceViewProvider {

    @Composable
    fun DeviceConnectView(
        syncRuntimeInfo: SyncRuntimeInfo,
        deviceInteractionEnabled: Boolean,
        onEdit: (SyncRuntimeInfo) -> Unit,
    )

    @Composable
    fun SyncDeviceView(
        syncInfo: SyncInfo,
        action: @Composable () -> Unit,
    )
}
