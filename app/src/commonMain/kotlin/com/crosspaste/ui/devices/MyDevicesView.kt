package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.db.sync.SyncRuntimeInfo
import org.koin.compose.koinInject

@Composable
fun MyDevicesView(syncRuntimeInfos: List<SyncRuntimeInfo>) {
    val deviceScopeFactory = koinInject<DeviceScopeFactory>()
    Box(contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxWidth()) {
            for ((index, syncRuntimeInfo) in syncRuntimeInfos.withIndex()) {
                val scope =
                    remember(syncRuntimeInfo.appInstanceId) {
                        deviceScopeFactory.createDeviceScope(syncRuntimeInfo)
                    }

                scope.DeviceConnectView()

                if (index != syncRuntimeInfos.size - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}
