package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Devices
import com.composables.icons.materialsymbols.rounded.Refresh
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncState
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.InnerScaffold
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.titanic
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

@Composable
fun DevicesContentView(guideContent: (@Composable () -> Unit)? = null) {
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
    var showCurrentDeviceDialog by remember { mutableStateOf(false) }
    var offlineExpanded by rememberSaveable { mutableStateOf(false) }

    val (onlineDevices, offlineDevices) =
        remember(syncRuntimeInfos) {
            syncRuntimeInfos.partition { it.connectState != SyncState.DISCONNECTED }
        }

    LaunchedEffect(Unit) {
        syncManager.refresh { }
    }

    unverifiedSyncRuntimeInfo?.let {
        val scope =
            remember(it) {
                deviceScopeFactory.createDeviceScope(it)
            }
        scope.TrustDeviceView()
    }

    InnerScaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(tiny),
            ) {
                FilledTonalButton(
                    onClick = { showCurrentDeviceDialog = true },
                    contentPadding = PaddingValues(horizontal = medium, vertical = tiny),
                    elevation =
                        ButtonDefaults.filledTonalButtonElevation(
                            defaultElevation = tiny3X,
                            pressedElevation = tiny5X,
                            hoveredElevation = tiny2X,
                            focusedElevation = tiny3X,
                        ),
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(small),
                    )
                    Spacer(modifier = Modifier.width(tiny3X))
                    Text(
                        text = copywriter.getText("current_device"),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                ExtendedFloatingActionButton(
                    onClick = {
                        showAddDeviceDialog = true
                    },
                    containerColor = LocalThemeExtState.current.success.surface,
                    contentColor = LocalThemeExtState.current.success.onContainer,
                    icon = { Icon(MaterialSymbols.Rounded.Add, contentDescription = null) },
                    text = { Text(copywriter.getText("add_device_manually")) },
                )
            }
        },
    ) { innerPadding ->

        if (showAddDeviceDialog) {
            AddDeviceDialog {
                showAddDeviceDialog = false
            }
        }

        if (showCurrentDeviceDialog) {
            CurrentDeviceDialog {
                showCurrentDeviceDialog = false
            }
        }

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = medium),
            contentPadding = PaddingValues(bottom = titanic),
            verticalArrangement = Arrangement.spacedBy(tiny),
        ) {
            if (syncRuntimeInfos.isNotEmpty()) {
                stickyHeader {
                    SectionHeader(
                        text = "my_devices",
                        trailingContent =
                            if (offlineDevices.isNotEmpty()) {
                                {
                                    OfflineToggle(
                                        count = offlineDevices.size,
                                        checked = offlineExpanded,
                                        onCheckedChange = { offlineExpanded = it },
                                    )
                                }
                            } else {
                                null
                            },
                    )
                }
            }

            items(onlineDevices, key = { it.appInstanceId }) { syncRuntimeInfo ->
                SyncDeviceItem(deviceScopeFactory, syncRuntimeInfo)
            }

            if (offlineExpanded) {
                items(offlineDevices, key = { "offline-${it.appInstanceId}" }) { syncRuntimeInfo ->
                    SyncDeviceItem(deviceScopeFactory, syncRuntimeInfo)
                }
            }

            stickyHeader {
                SectionHeader(
                    text = "nearby_devices",
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    topPadding =
                        if (syncRuntimeInfos.isNotEmpty()) {
                            medium
                        } else {
                            zero
                        },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                pasteBonjourService.refreshAll()
                            },
                            modifier = Modifier.size(xxLarge),
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Refresh,
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
                    NotFoundNearByDevices(guideContent = guideContent)
                }
            }
        }
    }
}

@Composable
private fun SyncDeviceItem(
    deviceScopeFactory: DeviceScopeFactory,
    syncRuntimeInfo: SyncRuntimeInfo,
) {
    val scope =
        remember(syncRuntimeInfo) {
            deviceScopeFactory.createDeviceScope(syncRuntimeInfo)
        }
    scope.DeviceConnectView()
}

@Composable
private fun OfflineToggle(
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tiny2X),
    ) {
        Switch(
            modifier = Modifier.scale(0.8f),
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = "${copywriter.getText(if (checked) "hide_offline" else "show_offline")} ($count)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}
