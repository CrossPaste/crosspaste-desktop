package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Block
import com.composables.icons.materialsymbols.rounded.Link
import com.crosspaste.app.AppControl
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.ui.theme.AppUISize.tiny
import org.koin.compose.koinInject

/**
 * Block / Pair for a discovered-but-unpaired device. Shared by the nearby list
 * row and the nearby detail header so both places offer the same actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScope.NearbyDeviceActions() {
    val appControl = koinInject<AppControl>()
    val nearbyDeviceManager = koinInject<NearbyDeviceManager>()
    val notificationManager = koinInject<NotificationManager>()
    val syncManager = koinInject<SyncManager>()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tiny),
    ) {
        GeneralIconButton(
            imageVector = MaterialSymbols.Rounded.Block,
            desc = "block",
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
        ) {
            blockNearbyDevice(nearbyDeviceManager, notificationManager)
        }

        GeneralIconButton(
            imageVector = MaterialSymbols.Rounded.Link,
            desc = "pair",
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            pairNearbyDevice(appControl, syncManager)
        }
    }
}

/**
 * Block the nearby device and tell the user where it went, since the row
 * vanishes on block and the blacklist should stay findable. Plain function so
 * non-button entry points (e.g. a swipe menu) can trigger the same action.
 */
fun SyncScope.blockNearbyDevice(
    nearbyDeviceManager: NearbyDeviceManager,
    notificationManager: NotificationManager,
) {
    nearbyDeviceManager.blockDevice(syncInfo)
    val deviceName = getDeviceDisplayName()
    notificationManager.sendNotification(
        title = { "${it.getText("device_blocked")}: $deviceName" },
        message = { it.getText("device_blocked_desc") },
        messageType = MessageType.Info,
        duration = 5000,
    )
}

/**
 * Pair with the nearby device if the device-connection limit allows one more
 * connection. Plain function so non-button entry points can trigger it.
 */
fun SyncScope.pairNearbyDevice(
    appControl: AppControl,
    syncManager: SyncManager,
) {
    if (appControl.isDeviceConnectionEnabled(syncManager.getSyncHandlers().size + 1)) {
        syncManager.updateSyncInfo(syncInfo)
    }
}
