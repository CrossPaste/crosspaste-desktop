package com.crosspaste.ui.devices

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.crosspaste.app.AppControl
import com.crosspaste.db.sync.SyncState
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.GeneralIconButton
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScope.DeviceActionButton() {
    val appControl = koinInject<AppControl>()
    val notificationManager = koinInject<NotificationManager>()
    val syncManager = koinInject<SyncManager>()

    val infiniteTransition = rememberInfiniteTransition(label = "RefreshRotation")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "RotationAngle",
    )

    if (syncRuntimeInfo.connectState == SyncState.CONNECTING ||
        syncRuntimeInfo.connectState == SyncState.DISCONNECTED
    ) {
        GeneralIconButton(
            imageVector = Icons.Default.Refresh,
            desc = "refresh",
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            iconModifier =
                Modifier.graphicsLayer {
                    rotationZ = if (refreshing) rotation else 0f
                },
        ) {
            runCatching {
                refreshing = true
                syncManager.refresh(listOf(syncRuntimeInfo.appInstanceId)) {
                    refreshing = false
                }
            }.onFailure { e ->
                refreshing = false
                notificationManager.sendNotification(
                    title = { it.getText("refresh_connection_failed") },
                    message = e.message?.let { message -> { message } },
                    messageType = MessageType.Error,
                )
            }
        }
    } else if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
        GeneralIconButton(
            imageVector = Icons.Default.Link,
            desc = "pair",
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            if (appControl.isDeviceConnectionEnabled(syncManager.getSyncHandlers().size + 1)) {
                syncManager.toVerify(syncRuntimeInfo.appInstanceId)
            }
        }
    }
}
