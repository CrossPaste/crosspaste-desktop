package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable

@Composable
fun SyncScope.NearbyDeviceDetailHeaderView() {
    DeviceRowContent(
        style = nearbyDeviceStyle,
    )
}
