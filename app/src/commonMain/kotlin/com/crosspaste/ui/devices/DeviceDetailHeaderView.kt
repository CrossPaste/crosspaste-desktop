package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable

@Composable
fun DeviceScope.DeviceDetailHeaderView(
    connecting: Boolean,
    trailingContent: @Composable DeviceScope.() -> Unit,
) {
    DeviceRowContent(
        style = myDeviceDetailStyle,
        iconTint = syncStateVisual(connecting).iconColor,
        trailingContent = { trailingContent() },
    )
}
