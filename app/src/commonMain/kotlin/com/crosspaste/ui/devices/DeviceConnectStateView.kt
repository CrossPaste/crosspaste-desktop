package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.db.sync.SyncState
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.refresh
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.tiny
import org.koin.compose.koinInject

@Composable
fun DeviceScope.DeviceConnectStateView(background: Color) {
    val copywriter = koinInject<GlobalCopywriter>()
    val notificationManager = koinInject<NotificationManager>()
    val syncManager = koinInject<SyncManager>()

    var refreshing by remember { mutableStateOf(false) }

    var syncHandler by remember(syncRuntimeInfo.appInstanceId) {
        mutableStateOf(syncManager.getSyncHandler(syncRuntimeInfo.appInstanceId))
    }

    val versionRelation by syncHandler?.versionRelation?.collectAsState() ?: remember {
        mutableStateOf(null)
    }

    val (connectColor, connectText) =
        getConnectStateColorAndText(
            versionRelation = versionRelation,
            refresh = refreshing,
            background = background,
        )

    if (!refreshing) {
        PasteIconButton(
            size = large2X,
            onClick = {
                if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
                    syncManager.toVerify(syncRuntimeInfo.appInstanceId)
                } else {
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
            },
            modifier =
                Modifier
                    .background(Color.Transparent, CircleShape)
                    .padding(end = tiny),
        ) {
            Icon(
                painter = refresh(),
                contentDescription = "refresh",
                modifier = Modifier.size(large),
                tint = connectColor,
            )
        }
    } else {
        CircularProgressIndicator(
            modifier = Modifier.size(large),
            color = connectColor,
        )
        Spacer(modifier = Modifier.width(tiny))
    }
    Icon(
        painter = AllowSendAndReceiveImage(),
        contentDescription = "connectState",
        modifier = Modifier.size(large),
        tint = connectColor,
    )
    Spacer(modifier = Modifier.width(tiny))
    Text(
        text = copywriter.getText(connectText),
        color = connectColor,
        style = AppUIFont.deviceConnectStateTextStyle,
    )
}
