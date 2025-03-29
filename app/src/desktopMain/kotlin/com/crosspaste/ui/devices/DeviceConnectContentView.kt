package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.AppWindowManager
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncState
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.DeviceDetail
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.getMenWidth
import com.crosspaste.ui.base.moreVertical
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DeviceConnectContentView(
    syncRuntimeInfo: SyncRuntimeInfo,
    deviceInteractionEnabled: Boolean,
    onEdit: (SyncRuntimeInfo) -> Unit,
) {
    val density = LocalDensity.current
    val appWindowManager = koinInject<AppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val notificationManager = koinInject<NotificationManager>()
    val syncManager = koinInject<SyncManager>()

    val scope = rememberCoroutineScope()

    var refresh by remember { mutableStateOf(false) }

    var syncHandler by remember(syncRuntimeInfo.appInstanceId) {
        mutableStateOf(syncManager.getSyncHandler(syncRuntimeInfo.appInstanceId))
    }

    val connectIcon = AllowSendAndReceiveImage(syncRuntimeInfo)

    var hover by remember { mutableStateOf(false) }
    val backgroundColor =
        if (hover) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }

    val (connectColor, connectText) =
        getConnectStateColorAndText(
            syncRuntimeInfo,
            syncHandler?.versionRelation,
            refresh,
            backgroundColor,
        )

    var modifier =
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(backgroundColor)

    if (deviceInteractionEnabled) {
        modifier =
            modifier.onPointerEvent(
                eventType = PointerEventType.Enter,
                onEvent = {
                    hover = true
                },
            ).onPointerEvent(
                eventType = PointerEventType.Exit,
                onEvent = {
                    hover = false
                },
            ).clickable {
                if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
                    syncManager.toVerify(syncRuntimeInfo.appInstanceId)
                } else {
                    appWindowManager.toScreen(DeviceDetail, syncRuntimeInfo)
                }
            }
    }

    DeviceBarView(
        modifier = modifier,
        background = backgroundColor,
        syncRuntimeInfo = syncRuntimeInfo,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            if (deviceInteractionEnabled) {
                if (!refresh) {
                    PasteIconButton(
                        size = 20.dp,
                        onClick = {
                            if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
                                syncManager.toVerify(syncRuntimeInfo.appInstanceId)
                            } else {
                                scope.launch {
                                    runCatching {
                                        refresh = true
                                        syncManager.resolveSync(syncRuntimeInfo.appInstanceId)
                                        delay(1000)
                                    }.onFailure { e ->
                                        notificationManager.sendNotification(
                                            title = { it.getText("refresh_connection_failed") },
                                            message = e.message?.let { message -> { message } },
                                            messageType = MessageType.Error,
                                        )
                                    }.apply {
                                        refresh = false
                                    }
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .background(Color.Transparent, CircleShape)
                                .padding(end = 8.dp),
                    ) {
                        Icon(
                            painter = com.crosspaste.ui.base.refresh(),
                            contentDescription = "refresh",
                            modifier = Modifier.size(18.dp),
                            tint = connectColor,
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = connectColor,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Icon(
                painter = connectIcon,
                contentDescription = "connectState",
                modifier = Modifier.size(18.dp),
                tint = connectColor,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = copywriter.getText(connectText),
                style =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        color = connectColor,
                        fontSize = 14.sp,
                    ),
            )

            if (deviceInteractionEnabled) {
                var showPopup by remember { mutableStateOf(false) }

                var buttonPosition by remember { mutableStateOf(Offset.Zero) }
                var buttonSize by remember { mutableStateOf(Size(0.0f, 0.0f)) }

                PasteIconButton(
                    size = 20.dp,
                    onClick = {
                        showPopup = !showPopup
                    },
                    modifier =
                        Modifier
                            .background(Color.Transparent, CircleShape)
                            .onGloballyPositioned { coordinates ->
                                buttonPosition = coordinates.localToWindow(Offset.Zero)
                                buttonSize = coordinates.size.toSize()
                            }.padding(horizontal = 8.dp),
                ) {
                    Icon(
                        painter = moreVertical(),
                        contentDescription = "info",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.contentColorFor(backgroundColor),
                    )
                }

                if (showPopup) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset =
                            IntOffset(
                                with(density) { ((-40).dp).roundToPx() },
                                with(density) { (20.dp).roundToPx() },
                            ),
                        onDismissRequest = {
                            if (showPopup) {
                                showPopup = false
                            }
                        },
                        properties =
                            PopupProperties(
                                focusable = true,
                                dismissOnBackPress = true,
                                dismissOnClickOutside = true,
                            ),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .wrapContentSize()
                                    .background(Color.Transparent)
                                    .shadow(15.dp),
                        ) {
                            val menuTexts =
                                arrayOf(
                                    copywriter.getText("add_note"),
                                    copywriter.getText("remove_device"),
                                )

                            val maxWidth = getMenWidth(menuTexts)

                            Column(
                                modifier =
                                    Modifier
                                        .width(maxWidth)
                                        .wrapContentHeight()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colorScheme.surfaceBright),
                            ) {
                                MenuItem(copywriter.getText("add_note")) {
                                    onEdit(syncRuntimeInfo)
                                    showPopup = false
                                }
                                MenuItem(copywriter.getText("remove_device")) {
                                    val id = syncRuntimeInfo.appInstanceId
                                    syncManager.removeSyncHandler(id)
                                    showPopup = false
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }
        }
    }
}
