package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.crosspaste.realm.sync.SyncRuntimeInfo
import com.crosspaste.ui.ScreenContext

@Composable
expect fun DeviceConnectView(
    syncRuntimeInfo: SyncRuntimeInfo,
    currentScreenContext: MutableState<ScreenContext>,
    deviceInteractionEnabled: Boolean,
    onEdit: (SyncRuntimeInfo) -> Unit,
)
