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
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.crosspaste.app.AppUpdateService
import com.crosspaste.db.sync.SyncState
import com.crosspaste.net.VersionRelation
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.GeneralIconButton
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScope.DeviceActionButton() {
    val appUpdateService = koinInject<AppUpdateService>()
    val notificationManager = koinInject<NotificationManager>()
    val syncManager = koinInject<SyncManager>()

    when (syncRuntimeInfo.connectState) {
        SyncState.CONNECTING, SyncState.DISCONNECTED,
        -> {
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
        }
        SyncState.UNVERIFIED -> {
            GeneralIconButton(
                imageVector = Icons.Default.Link,
                desc = "pair",
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                syncManager.toVerify(syncRuntimeInfo.appInstanceId)
            }
        }
        SyncState.INCOMPATIBLE -> {
            val versionRelation by syncManager
                .getSyncHandler(syncRuntimeInfo.appInstanceId)
                ?.versionRelation
                ?.collectAsState() ?: remember { mutableStateOf(null) }

            if (versionRelation == VersionRelation.LOWER_THAN) {
                GeneralIconButton(
                    imageVector = Icons.Default.Upgrade,
                    desc = "upgrade",
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                ) {
                    appUpdateService.tryTriggerUpdate()
                }
            }
        }
    }
}
