package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import com.crosspaste.ui.WindowDecoration

@Composable
actual fun DeviceDetailScreen() {
    WindowDecoration("device_detail")
    DeviceDetailContentView()
}
