package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.theme.AppUISize.medium

@Composable
fun DeviceScope.DeviceDetailHeaderView() {
    StaticDeviceBarView { background ->
        DeviceConnectStateView(background)
        Spacer(modifier = Modifier.width(medium))
    }
}
