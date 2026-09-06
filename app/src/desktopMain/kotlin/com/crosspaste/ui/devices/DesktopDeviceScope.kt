package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    @Composable
    override fun TrustDeviceView() {
        TrustDeviceDialog()
    }

    @Composable
    override fun DeviceConnectView() {
        val navigationManager = koinInject<NavigationManager>()

        DeviceRowContent(
            onClick = {
                navigationManager.navigate(DeviceDetail(syncRuntimeInfo.appInstanceId))
            },
            style = myDeviceStyle,
            trailingContent = { DeviceActions() },
        )
    }
}

/** Status tag + refresh/pair/upgrade + menu, shared by the list row and the detail header. */
@Composable
fun DeviceScope.DeviceActions() {
    var refreshing by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SyncStateTag(refreshing)
        DeviceActionButton(refreshing) {
            refreshing = it
        }
        MyDeviceMenuButton()
    }
}
