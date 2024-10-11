package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import com.crosspaste.realm.sync.SyncRuntimeInfo

@Composable
expect fun DeviceConnectView(
    syncRuntimeInfo: SyncRuntimeInfo,
    deviceInteractionEnabled: Boolean,
    onEdit: (SyncRuntimeInfo) -> Unit,
)
