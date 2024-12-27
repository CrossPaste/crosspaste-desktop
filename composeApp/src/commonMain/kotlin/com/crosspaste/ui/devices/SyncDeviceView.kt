package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.crosspaste.dto.sync.SyncInfo

@Composable
expect fun SyncDeviceView(
    syncInfo: SyncInfo,
    action: @Composable (Color) -> Unit,
)
