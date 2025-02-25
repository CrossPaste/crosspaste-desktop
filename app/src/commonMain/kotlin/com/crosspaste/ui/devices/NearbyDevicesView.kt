package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.crosspaste.sync.NearbyDeviceManager
import org.koin.compose.koinInject

@Composable
fun NearbyDevicesView() {
    val nearbyDeviceManager = koinInject<NearbyDeviceManager>()

    val nearbyDevicesList by nearbyDeviceManager.syncInfos.collectAsState()

    val searching by nearbyDeviceManager.searching.collectAsState()

    if (searching) {
        SearchNearByDevices()
    } else if (nearbyDevicesList.isEmpty()) {
        NotFoundNearByDevices()
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            for ((index, syncInfo) in nearbyDevicesList.withIndex()) {
                NearbyDeviceView(syncInfo)
                if (index != nearbyDevicesList.size - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}
