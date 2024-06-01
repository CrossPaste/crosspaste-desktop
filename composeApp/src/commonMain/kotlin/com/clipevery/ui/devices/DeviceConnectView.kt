package com.clipevery.ui.devices

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
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncState
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.sync.SyncManager
import com.clipevery.ui.PageViewContext
import com.clipevery.ui.PageViewType
import com.clipevery.ui.base.ClipIconButton
import com.clipevery.ui.base.MenuItem
import com.clipevery.ui.base.arrowLeftIcon
import com.clipevery.ui.base.arrowRightIcon
import com.clipevery.ui.base.block
import com.clipevery.ui.base.getMenWidth
import com.clipevery.ui.base.syncAlt
import com.clipevery.ui.hoverSurfaceColor

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DeviceConnectView(
    syncRuntimeInfo: SyncRuntimeInfo,
    currentPageViewContext: MutableState<PageViewContext>,
    deviceInteractionEnabled: Boolean,
    onEdit: (SyncRuntimeInfo) -> Unit,
) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val syncManager = current.koin.get<SyncManager>()

    val (connectColor, connectText) =
        if (syncRuntimeInfo.allowSend || syncRuntimeInfo.allowReceive) {
            getConnectStateColorAndText(syncRuntimeInfo.connectState)
        } else {
            Pair(Color.Red, "OFF_CONNECTED")
        }

    val connectIcon = getAllowSendAndReceiveImage(syncRuntimeInfo)

    var hover by remember { mutableStateOf(false) }
    val backgroundColor = if (hover) MaterialTheme.colors.hoverSurfaceColor() else MaterialTheme.colors.background

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
                    currentPageViewContext.value =
                        PageViewContext(
                            PageViewType.DEVICE_DETAIL,
                            currentPageViewContext.value,
                            syncRuntimeInfo,
                        )
                }
            }
    }

    DeviceBarView(
        modifier = modifier,
        syncRuntimeInfo,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Icon(
                connectIcon,
                contentDescription = "connectState",
                modifier = Modifier.size(16.dp),
                tint = connectColor,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = copywriter.getText(connectText),
                style =
                    TextStyle(
                        fontWeight = FontWeight.Light,
                        color = connectColor,
                        fontSize = 14.sp,
                    ),
            )

            if (deviceInteractionEnabled) {
                var showPopup by remember { mutableStateOf(false) }

                var buttonPosition by remember { mutableStateOf(Offset.Zero) }
                var buttonSize by remember { mutableStateOf(Size(0.0f, 0.0f)) }

                ClipIconButton(
                    radius = 18.dp,
                    onClick = {
                        showPopup = !showPopup
                    },
                    modifier =
                        Modifier
                            .background(Color.Transparent, CircleShape)
                            .onGloballyPositioned { coordinates ->
                                buttonPosition = coordinates.localToWindow(Offset.Zero)
                                buttonSize = coordinates.size.toSize()
                            },
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "info",
                        modifier = Modifier.padding(3.dp).size(18.dp),
                        tint = MaterialTheme.colors.primary,
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
                                    copywriter.getText("Add_Note"),
                                    copywriter.getText("Remove_Device"),
                                )

                            val maxWidth = getMenWidth(menuTexts)

                            Column(
                                modifier =
                                    Modifier
                                        .width(maxWidth)
                                        .wrapContentHeight()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colors.surface),
                            ) {
                                MenuItem(copywriter.getText("Add_Note")) {
                                    onEdit(syncRuntimeInfo)
                                    showPopup = false
                                }
                                MenuItem(copywriter.getText("Remove_Device")) {
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

@Composable
fun getAllowSendAndReceiveImage(syncRuntimeInfo: SyncRuntimeInfo): ImageVector {
    return if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
        Icons.Outlined.Lock
    } else if (syncRuntimeInfo.allowSend && syncRuntimeInfo.allowReceive) {
        syncAlt()
    } else if (syncRuntimeInfo.allowSend) {
        arrowLeftIcon()
    } else if (syncRuntimeInfo.allowReceive) {
        arrowRightIcon()
    } else {
        block()
    }
}

fun getConnectStateColorAndText(connectState: Int): Pair<Color, String> {
    return when (connectState) {
        SyncState.CONNECTED -> Pair(Color.Green, "CONNECTED")
        SyncState.CONNECTING -> Pair(Color.Gray, "CONNECTING")
        SyncState.DISCONNECTED -> Pair(Color.Red, "DISCONNECTED")
        SyncState.UNMATCHED -> Pair(Color.Yellow, "UNMATCHED")
        SyncState.UNVERIFIED -> Pair(Color(0xFFFFA500), "UNVERIFIED")
        else -> Pair(Color.Red, "OFF_CONNECTED")
    }
}
