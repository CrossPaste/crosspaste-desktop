package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.ui.theme.AppUIColors
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
        val lazyListState = rememberLazyListState()
        var isScrollable by remember { mutableStateOf(false) }

        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(AppUIColors.deviceBackground),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            val contentHeight =
                                lazyListState.layoutInfo.totalItemsCount *
                                    (lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0)
                            isScrollable = contentHeight > coordinates.size.height
                        },
            ) {
                itemsIndexed(nearbyDevicesList) { index, syncInfo ->
                    NearbyDeviceView(syncInfo)

                    if (index != nearbyDevicesList.size - 1 || !isScrollable) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
