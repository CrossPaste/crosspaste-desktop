package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.ExpandViewProvider
import com.crosspaste.ui.base.rememberExpandableState
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
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
            Modifier
                .fillMaxSize()
                .background(AppUIColors.appBackground)
                .padding(horizontal = medium)
                .padding(bottom = medium)
                .clip(tinyRoundedCornerShape),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val syncRuntimeInfos by syncManager.realTimeSyncRuntimeInfos.collectAsState()

            if (syncRuntimeInfos.isNotEmpty()) {
                expandViewProvider.ExpandView(
                    state = rememberExpandableState(true),
                    horizontalPadding = zero,
                    barContent = {
                        expandViewProvider.ExpandBarView(
                            state = this.state,
                            title = "my_devices",
                        )
                    },
                ) {
                    MyDevicesView(syncRuntimeInfos)
                }
                Spacer(modifier = Modifier.height(small3X))
            }

            expandViewProvider.ExpandView(
                horizontalPadding = zero,
                barContent = {
                    expandViewProvider.ExpandBarView(
                        state = this.state,
                        title = "add_device_manually",
                    )
                },
            ) {
                AddDeviceManuallyView()
            }
            Spacer(modifier = Modifier.height(small3X))
            expandViewProvider.ExpandView(
                state = rememberExpandableState(true),
                horizontalPadding = zero,
                barContent = {
                    expandViewProvider.ExpandBarView(
                        state = this.state,
                        title = "nearby_devices",
                    )
                },
            ) {
                NearbyDevicesView()
            }
        }
    }
}
