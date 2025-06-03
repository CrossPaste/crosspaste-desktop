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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.AppSize
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
import com.crosspaste.ui.base.moreVertical
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUIFont.getFontWidth
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
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
    val appSize = koinInject<AppSize>()
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
    val background =
        if (hover) {
            AppUIColors.selectedDeviceBackground
        } else {
            AppUIColors.generalBackground
        }

    val (connectColor, connectText) =
        getConnectStateColorAndText(
            syncRuntimeInfo = syncRuntimeInfo,
            versionRelation = syncHandler?.versionRelation,
            refresh = refresh,
            background = background,
        )

    var modifier =
        Modifier
            .fillMaxWidth()
            .height(appSize.deviceHeight)
            .background(background)

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
        background = background,
        syncRuntimeInfo = syncRuntimeInfo,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(start = medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            if (deviceInteractionEnabled) {
                if (!refresh) {
                    PasteIconButton(
                        size = large2X,
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
                                .padding(end = tiny),
                    ) {
                        Icon(
                            painter = com.crosspaste.ui.base.refresh(),
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
            }
            Icon(
                painter = connectIcon,
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

            if (deviceInteractionEnabled) {
                var showPopup by remember { mutableStateOf(false) }

                PasteIconButton(
                    size = large2X,
                    onClick = {
                        showPopup = !showPopup
                    },
                    modifier =
                        Modifier
                            .background(Color.Transparent, CircleShape)
                            .padding(horizontal = tiny),
                ) {
                    Icon(
                        painter = moreVertical(),
                        contentDescription = "info",
                        modifier = Modifier.size(large),
                        tint =
                            MaterialTheme.colorScheme.contentColorFor(
                                background,
                            ),
                    )
                }

                if (showPopup) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset =
                            IntOffset(
                                x = with(density) { ((-40).dp).roundToPx() },
                                y = with(density) { (large2X).roundToPx() },
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
                                    .shadow(small),
                        ) {
                            val menuTexts =
                                arrayOf(
                                    copywriter.getText("add_note"),
                                    copywriter.getText("remove_device"),
                                )

                            val maxWidth = getFontWidth(menuTexts)

                            Column(
                                modifier =
                                    Modifier
                                        .width(maxWidth)
                                        .wrapContentHeight()
                                        .clip(tiny2XRoundedCornerShape)
                                        .background(AppUIColors.menuBackground),
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
                Spacer(modifier = Modifier.width(medium))
            }
        }
    }
}
