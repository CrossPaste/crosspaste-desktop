package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable

@Composable
fun DeviceScope.DeviceDetailHeaderView(trailingContent: @Composable DeviceScope.() -> Unit) {
    DeviceRowContent(
        style = myDeviceDetailStyle,
        trailingContent = { trailingContent() },
    )
}
