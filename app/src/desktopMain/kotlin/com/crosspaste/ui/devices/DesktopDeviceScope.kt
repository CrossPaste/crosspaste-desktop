package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.ui.DeviceDetail
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.theme.AppUISize.tiny
import org.koin.compose.koinInject

class DesktopDeviceScope(
    override var syncRuntimeInfo: SyncRuntimeInfo,
) : DeviceScope {

    override var refreshing: Boolean by mutableStateOf(false)

    @Composable
    override fun DeviceConnectView() {
        val navigationManager = koinInject<NavigationManager>()
        DeviceRowContent(
            onClick = {
                navigationManager.navigate(DeviceDetail(syncRuntimeInfo.appInstanceId))
            },
            style = myDeviceStyle,
            tagContent = {
                SyncStateTag()
            },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(tiny),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeviceActionButton()
                    MyDeviceMenuButton()
                }
            },
        )
    }
}
