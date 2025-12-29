package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.InnerScaffold
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxLarge
import org.koin.compose.koinInject

@Composable
fun DevicesContentView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val deviceScopeFactory = koinInject<DeviceScopeFactory>()
    val nearbyDeviceManager = koinInject<NearbyDeviceManager>()
    val pasteBonjourService = koinInject<PasteBonjourService>()
    val syncManager = koinInject<SyncManager>()
    val syncScopeFactory = koinInject<SyncScopeFactory>()

    val nearbyDevicesList by nearbyDeviceManager.nearbySyncInfos.collectAsState()

    val searching by nearbyDeviceManager.searching.collectAsState()

    val syncRuntimeInfos by syncManager.realTimeSyncRuntimeInfos.collectAsState()

    val unverifiedSyncRuntimeInfo by syncManager.unverifiedSyncRuntimeInfo.collectAsState()

    var showAddDeviceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        syncManager.refresh { }
    }

    unverifiedSyncRuntimeInfo?.let {
        val scope =
            remember(it) {
                deviceScopeFactory.createDeviceScope(it)
            }
        scope.TrustDeviceDialog()
    }

    InnerScaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    showAddDeviceDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(copywriter.getText("add_device_manually")) },
            )
        },
    ) { innerPadding ->

        if (showAddDeviceDialog) {
            AddDeviceDialog {
                showAddDeviceDialog = false
            }
        }

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = medium),
            verticalArrangement = Arrangement.spacedBy(small2X),
        ) {
            if (syncRuntimeInfos.isNotEmpty()) {
                stickyHeader {
                    SectionHeader(copywriter.getText("my_devices"))
                }
            }

            items(syncRuntimeInfos) { syncRuntimeInfo ->
                val deviceScopeFactory = koinInject<DeviceScopeFactory>()
                val scope =
                    remember(syncRuntimeInfo) {
                        deviceScopeFactory.createDeviceScope(syncRuntimeInfo)
                    }

                scope.DeviceConnectView()
            }

            stickyHeader {
                if (syncRuntimeInfos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(tiny))
                }
                SectionHeader(
                    text = copywriter.getText("nearby_devices"),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                pasteBonjourService.refreshAll()
                            },
                            modifier = Modifier.size(xxLarge),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "refresh",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(large2X),
                            )
                        }
                    },
                )
            }

            if (searching) {
                item {
                    SearchingNearbyDevices()
                }
            } else if (nearbyDevicesList.isNotEmpty()) {
                items(nearbyDevicesList, key = { item -> item.appInfo.appInstanceId }) { syncInfo ->
                    val currentSyncInfo by rememberUpdatedState(syncInfo)
                    val scope =
                        remember(currentSyncInfo) {
                            syncScopeFactory.createSyncScope(currentSyncInfo)
                        }
                    scope.NearbyDeviceView()
                }
            } else {
                item(key = "empty_nearby_devices") {
                    NotFoundNearByDevices()
                }
            }
        }
    }
}
