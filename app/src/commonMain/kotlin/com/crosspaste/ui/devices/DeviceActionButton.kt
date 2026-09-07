package com.crosspaste.ui.devices

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Link
import com.composables.icons.materialsymbols.rounded.Refresh
import com.composables.icons.materialsymbols.rounded.Upgrade
import com.crosspaste.app.AppUpdateService
import com.crosspaste.db.sync.SyncState
import com.crosspaste.net.VersionRelation
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.utils.getControlUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScope.DeviceActionButton(
    connecting: Boolean,
    updateRefreshing: (Boolean) -> Unit,
) {
    val appUpdateService = koinInject<AppUpdateService>()
    val notificationManager = koinInject<NotificationManager>()
    val syncManager = koinInject<SyncManager>()

    val scope = rememberCoroutineScope()

    val connectState = syncRuntimeInfo.connectState

    when (connectState) {
        SyncState.CONNECTING, SyncState.DISCONNECTED,
        -> {
            // [connecting] already covers both the manual refresh and the automatic
            // reconnect, so a re-probing device does not look plainly offline.
            val tag = syncStateVisual(connecting).tag
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
                imageVector = MaterialSymbols.Rounded.Refresh,
                desc = "refresh",
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = tag.containerColor,
                        contentColor = tag.contentColor,
                    ),
                iconModifier =
                    Modifier.graphicsLayer {
                        rotationZ = if (connecting) rotation else 0f
                    },
            ) {
                scope.launch {
                    try {
                        updateRefreshing(true)
                        getControlUtils().ensureMinExecutionTimeForCallback(2000L) { proceed ->
                            syncManager.refresh(listOf(syncRuntimeInfo.appInstanceId)) {
                                proceed()
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error(e) { "refresh failed: ${syncRuntimeInfo.appInstanceId}" }
                        notificationManager.sendNotification(
                            title = { it.getText("refresh_connection_failed") },
                            messageType = MessageType.Error,
                        )
                    } finally {
                        updateRefreshing(false)
                    }
                }
            }
        }
        SyncState.UNVERIFIED -> {
            GeneralIconButton(
                imageVector = MaterialSymbols.Rounded.Link,
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
                    imageVector = MaterialSymbols.Rounded.Upgrade,
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
