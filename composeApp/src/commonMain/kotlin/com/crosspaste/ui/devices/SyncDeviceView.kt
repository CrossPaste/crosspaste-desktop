package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import com.crosspaste.dto.sync.SyncInfo

@Composable
expect fun SyncDeviceView(
    syncInfo: SyncInfo,
    action: @Composable () -> Unit,
)
