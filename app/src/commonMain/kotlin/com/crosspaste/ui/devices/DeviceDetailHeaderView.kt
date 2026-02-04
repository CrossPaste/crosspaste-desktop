package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun DeviceScope.DeviceDetailHeaderView() {
    var refreshing by remember { mutableStateOf(false) }
    DeviceRowContent(
        style = myDeviceDetailStyle,
        trailingContent = {
            SyncStateTag(refreshing)
        },
    )
}
