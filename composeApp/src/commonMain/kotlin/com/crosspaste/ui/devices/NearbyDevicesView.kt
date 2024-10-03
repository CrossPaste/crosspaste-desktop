package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.crosspaste.sync.DeviceManager
import org.koin.compose.koinInject

@Composable
fun NearbyDevicesView() {
    val deviceManager = koinInject<DeviceManager>()

    val nearbyDevicesList = remember { deviceManager.syncInfos }

    if (deviceManager.searching) {
        SearchNearByDevices()
    } else if (nearbyDevicesList.isEmpty()) {
        NotFoundNearByDevices()
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            for ((index, syncInfo) in nearbyDevicesList.withIndex()) {
                NearbyDeviceView(syncInfo)
                if (index != nearbyDevicesList.size - 1) {
                    HorizontalDivider(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
