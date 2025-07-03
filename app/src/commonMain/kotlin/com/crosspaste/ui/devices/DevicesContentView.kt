package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.ExpandViewProvider
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

@Composable
fun DevicesContentView() {
    val expandViewProvider = koinInject<ExpandViewProvider>()
    val syncManager = koinInject<SyncManager>()

    LaunchedEffect(Unit) {
        syncManager.refresh { }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .clip(tinyRoundedCornerShape),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val syncRuntimeInfos by syncManager.realTimeSyncRuntimeInfos.collectAsState()

            if (syncRuntimeInfos.isNotEmpty()) {
                expandViewProvider.ExpandView(
                    horizontalPadding = zero,
                    defaultExpand = true,
                    barContent = { iconScale ->
                        expandViewProvider.ExpandBarView(
                            title = "my_devices",
                            iconScale = iconScale,
                        )
                    },
                ) {
                    MyDevicesView(syncRuntimeInfos)
                }
                Spacer(modifier = Modifier.height(small3X))
            }

            expandViewProvider.ExpandView(
                horizontalPadding = zero,
                defaultExpand = false,
                barContent = { iconScale ->
                    expandViewProvider.ExpandBarView(
                        title = "add_device_manually",
                        iconScale = iconScale,
                    )
                },
            ) {
                AddDeviceManuallyView()
            }
            Spacer(modifier = Modifier.height(small3X))
            expandViewProvider.ExpandView(
                horizontalPadding = zero,
                defaultExpand = true,
                barContent = { iconScale ->
                    expandViewProvider.ExpandBarView(
                        title = "nearby_devices",
                        iconScale = iconScale,
                    )
                },
            ) {
                NearbyDevicesView()
            }
        }
    }
}
