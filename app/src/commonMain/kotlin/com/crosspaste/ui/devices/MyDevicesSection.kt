package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

/**
 * Builds the sticky online / offline device groups from [SyncManager]. Shared by
 * desktop and mobile so the flicker-free grouping lives in one place; only the
 * surrounding layout differs per platform.
 */
@Composable
fun rememberDeviceGroups(): DeviceGroups {
    val syncManager = koinInject<SyncManager>()
    val deviceGroups by remember {
        syncManager.realTimeSyncRuntimeInfos.scanDeviceGroups()
    }.collectAsState(DeviceGroups())
    return deviceGroups
}

/**
 * The shared "my devices" list: a header with the offline toggle, the online
 * devices, and (when expanded) the offline devices. Platform screens supply their
 * own scaffold, top bar and nearby-devices section around this.
 */
fun LazyListScope.myDevicesSection(
    deviceGroups: DeviceGroups,
    offlineExpanded: Boolean,
    onOfflineExpandedChange: (Boolean) -> Unit,
    deviceScopeFactory: DeviceScopeFactory,
    headerTopPadding: Dp = zero,
) {
    val onlineDevices = deviceGroups.online
    val offlineDevices = deviceGroups.offline

    if (deviceGroups.hasDevices) {
        stickyHeader {
            SectionHeader(
                text = "my_devices",
                topPadding = headerTopPadding,
                trailingContent =
                    if (offlineDevices.isNotEmpty()) {
                        {
                            OfflineToggle(
                                count = offlineDevices.size,
                                checked = offlineExpanded,
                                onCheckedChange = onOfflineExpandedChange,
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
            modifier = Modifier.scale(0.7f),
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
