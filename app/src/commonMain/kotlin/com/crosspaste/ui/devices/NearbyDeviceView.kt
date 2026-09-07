package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.NearbyDeviceDetail
import org.koin.compose.koinInject

@Composable
fun SyncScope.NearbyDeviceView() {
    val navigationManager = koinInject<NavigationManager>()

    DeviceRowContent(
        style = nearbyDeviceStyle,
        onClick = {
            navigationManager.navigate(NearbyDeviceDetail(syncInfo.appInfo.appInstanceId))
        },
        trailingContent = {
            NearbyDeviceActions()
        },
    )
}
