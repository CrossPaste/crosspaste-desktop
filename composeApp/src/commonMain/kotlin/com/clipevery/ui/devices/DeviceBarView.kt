package com.clipevery.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncState
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.platform.Platform
import com.clipevery.ui.PageViewContext
import com.clipevery.ui.PageViewType
import com.clipevery.ui.android
import com.clipevery.ui.icon.arrowLeftIcon
import com.clipevery.ui.icon.arrowRightIcon
import com.clipevery.ui.icon.block
import com.clipevery.ui.icon.syncAlt
import com.clipevery.ui.ipad
import com.clipevery.ui.iphone
import com.clipevery.ui.linux
import com.clipevery.ui.macos
import com.clipevery.ui.questionOS
import com.clipevery.ui.windows

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DeviceBarView(syncRuntimeInfo: SyncRuntimeInfo,
                  currentPageViewContext: MutableState<PageViewContext>,
                  deviceInteractionEnabled: Boolean) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()

    var hover by remember { mutableStateOf(false) }
    val backgroundColor = if (hover) MaterialTheme.colors.secondaryVariant else MaterialTheme.colors.background

    val (connectColor, connectText) = if (syncRuntimeInfo.allowSend || syncRuntimeInfo.allowReceive)  {
        getConnectStateColorAndText(syncRuntimeInfo.connectState)
    } else {
        Pair(Color.Red, "OFF_CONNECTED")
    }

    val connectIcon = getAllowSendAndReceiveImage(syncRuntimeInfo)

    val platform = Platform(syncRuntimeInfo.platformName,
        syncRuntimeInfo.platformArch,
        syncRuntimeInfo.platformBitMode,
        syncRuntimeInfo.platformVersion)

    val imageVector = if (platform.isMacos()) {
        macos()
    } else if (platform.isWindows()) {
        windows()
    } else if (platform.isLinux()) {
        linux()
    } else if (platform.isIphone()) {
        iphone()
    } else if (platform.isIpad()) {
        ipad()
    } else if (platform.isAndroid()) {
        android()
    } else {
        questionOS()
    }

    var modifier = Modifier
        .fillMaxWidth()
        .height(60.dp)
        .background(backgroundColor)

    if (deviceInteractionEnabled) {
        modifier = modifier.onPointerEvent(
            eventType = PointerEventType.Enter,
            onEvent = {
                hover = true
            }
        ).onPointerEvent(
            eventType = PointerEventType.Exit,
            onEvent = {
                hover = false
            }
        ).clickable {
            currentPageViewContext.value = PageViewContext(
                PageViewType.DEVICE_DETAIL,
                syncRuntimeInfo
            )
        }
    }


    Row(modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            modifier = Modifier.padding(12.dp).size(36.dp),
            imageVector = imageVector,
            contentDescription = "OS Icon",
            tint = MaterialTheme.colors.onBackground
        )

        Column(modifier = Modifier.align(Alignment.CenterVertically)) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = platform.name,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 17.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = platform.version,
                    style = TextStyle(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 15.sp
                    )
                )
            }

            Text(
                modifier = Modifier.fillMaxWidth(fraction = 0.5f),
                text = syncRuntimeInfo.deviceName,
                style = TextStyle(
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.weight(2f, fill = true))

        Icon(
            connectIcon,
            contentDescription = "connectState",
            modifier = Modifier.padding(16.dp).size(20.dp),
            tint = connectColor
        )

        Text(
            text = copywriter.getText(connectText),
            style = TextStyle(
                fontWeight = FontWeight.Light,
                color = connectColor,
                fontSize = 17.sp
            )
        )

        if (deviceInteractionEnabled) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "info",
                modifier = Modifier.padding(16.dp).size(20.dp),
                tint = MaterialTheme.colors.primary)
        } else {
            Spacer(modifier = Modifier.width(16.dp))
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
    return when(connectState) {
        SyncState.CONNECTED -> Pair(Color.Green, "CONNECTED")
        SyncState.CONNECTING -> Pair(Color.Gray, "CONNECTING")
        SyncState.DISCONNECTED -> Pair(Color.Red, "DISCONNECTED")
        SyncState.UNMATCHED -> Pair(Color.Yellow, "UNMATCHED")
        SyncState.UNVERIFIED -> Pair(Color(0xFFFFA500), "UNVERIFIED")
        else -> Pair(Color.Red, "OFF_CONNECTED")
    }
}
