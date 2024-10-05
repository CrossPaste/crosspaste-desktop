package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.LocalScreenContent
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.ExpandView
import com.crosspaste.ui.base.PasteDialog
import org.koin.compose.koinInject

@Composable
actual fun DevicesScreen() {
    val currentScreenContext = LocalScreenContent.current

    val syncManager = koinInject<SyncManager>()
    val dialogService = koinInject<DialogService>()

    LaunchedEffect(Unit) {
        syncManager.resolveSyncs()
    }

    LaunchedEffect(syncManager.waitToVerifySyncRuntimeInfo?.deviceId) {
        syncManager.waitToVerifySyncRuntimeInfo?.let { info ->
            dialogService.pushDialog(
                PasteDialog(
                    key = info.deviceId,
                    title = "do_you_trust_this_device?",
                    width = 320.dp,
                ) {
                    DeviceVerifyView(info)
                },
            )
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .padding(8.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surface.copy(0.64f)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (syncManager.realTimeSyncRuntimeInfos.isNotEmpty()) {
                ExpandView("my_devices", defaultExpand = true) {
                    MyDevicesView(currentScreenContext)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            ExpandView("add_device_manually", defaultExpand = false) {
                AddDeviceManuallyView()
            }
            Spacer(modifier = Modifier.height(10.dp))
            ExpandView("nearby_devices", defaultExpand = true) {
                NearbyDevicesView()
            }
        }
    }
}
