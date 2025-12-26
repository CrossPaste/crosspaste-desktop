package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable

@Composable
fun DeviceScope.DeviceDetailHeaderView() {
    DeviceRowContent(
        style = myDeviceDetailStyle,
        tagContent = { SyncStateTag() },
        trailingContent = { DeviceActionButton() },
    )
}
