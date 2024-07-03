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
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.painter.Painter
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
import com.crosspaste.LocalKoinApplication
import com.crosspaste.dao.sync.SyncRuntimeInfo
import com.crosspaste.dao.sync.SyncState
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.PageViewContext
import com.crosspaste.ui.PageViewType
import com.crosspaste.ui.base.ClipIconButton
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.allowReceive
import com.crosspaste.ui.base.allowSend
import com.crosspaste.ui.base.block
import com.crosspaste.ui.base.getMenWidth
import com.crosspaste.ui.base.sync
import com.crosspaste.ui.base.unverified
import com.crosspaste.ui.connectedColor
import com.crosspaste.ui.connectingColor
import com.crosspaste.ui.disconnectedColor
import com.crosspaste.ui.selectColor
import com.crosspaste.ui.unmatchedColor
import com.crosspaste.ui.unverifiedColor

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
    val backgroundColor = if (hover) MaterialTheme.colors.selectColor() else MaterialTheme.colors.background

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

                ClipIconButton(
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
                            }.padding(horizontal = 12.dp),
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "info",
                        modifier = Modifier.size(18.dp),
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
fun getAllowSendAndReceiveImage(syncRuntimeInfo: SyncRuntimeInfo): Painter {
    return if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
        unverified()
    } else if (syncRuntimeInfo.allowSend && syncRuntimeInfo.allowReceive) {
        sync()
    } else if (syncRuntimeInfo.allowSend) {
        allowSend()
    } else if (syncRuntimeInfo.allowReceive) {
        allowReceive()
    } else {
        block()
    }
}

fun getConnectStateColorAndText(connectState: Int): Pair<Color, String> {
    return when (connectState) {
        SyncState.CONNECTED -> Pair(connectedColor(), "CONNECTED")
        SyncState.CONNECTING -> Pair(connectingColor(), "CONNECTING")
        SyncState.DISCONNECTED -> Pair(disconnectedColor(), "DISCONNECTED")
        SyncState.UNMATCHED -> Pair(unmatchedColor(), "UNMATCHED")
        SyncState.UNVERIFIED -> Pair(unverifiedColor(), "UNVERIFIED")
        else -> throw IllegalArgumentException("Unknown connectState: $connectState")
    }
}
