package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.realm.sync.SyncRuntimeInfo

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
        action: @Composable (Color) -> Unit,
    )
}
