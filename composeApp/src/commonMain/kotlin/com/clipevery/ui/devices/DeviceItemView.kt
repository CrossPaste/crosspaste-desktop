package com.clipevery.ui.devices

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
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
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncState
import com.clipevery.i18n.Copywriter
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.platform.Platform
import com.clipevery.ui.android
import com.clipevery.ui.arrowLeftIcon
import com.clipevery.ui.arrowRightIcon
import com.clipevery.ui.block
import com.clipevery.ui.ipad
import com.clipevery.ui.iphone
import com.clipevery.ui.linux
import com.clipevery.ui.macos
import com.clipevery.ui.questionOS
import com.clipevery.ui.syncAlt
import com.clipevery.ui.windows

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceItemView(syncRuntimeInfo: SyncRuntimeInfo) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()

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

    Row(modifier = Modifier
        .fillMaxWidth()
        .height(60.dp)
        .background(MaterialTheme.colors.background),
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
            Row(modifier = Modifier.wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                Text(text = platform.name,
                    style = TextStyle(fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 17.sp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = platform.version,
                    style = TextStyle(fontWeight = FontWeight.Light,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 15.sp)
                )
            }

            Text(modifier = Modifier.fillMaxWidth(fraction = 0.5f),
                text = syncRuntimeInfo.deviceName,
                style = TextStyle(fontWeight = FontWeight.Light,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.weight(2f, fill = true))

        Icon(
            connectIcon,
            contentDescription = "connectState",
            modifier = Modifier.padding(16.dp).size(20.dp),
            tint = connectColor)

        Text(text = copywriter.getText(connectText),
            style = TextStyle(fontWeight = FontWeight.Light,
                color = connectColor,
                fontSize = 17.sp)
        )

        val detailInfo by remember { mutableStateOf(deviceDetailInfo(copywriter, syncRuntimeInfo)) }

        TooltipArea(
            tooltip = {
                // 你可以自定义这里的内容，例如使用一个Card或者其他的Composable来展示更多的信息
                Surface(
                    modifier = Modifier.padding(10.dp)
                ) {
                    Text(text = detailInfo)
                }
            },
            tooltipPlacement = TooltipPlacement.CursorPoint(
                alignment = Alignment.TopStart,
                offset = DpOffset(8.dp, 8.dp)
            )
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "info",
                modifier = Modifier.padding(16.dp).size(20.dp),
                tint = MaterialTheme.colors.primary)
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

fun deviceDetailInfo(copywriter: Copywriter, syncRuntimeInfo: SyncRuntimeInfo): String {
    return """
        |${copywriter.getText("Device_ID")}: ${syncRuntimeInfo.deviceId}
        |${copywriter.getText("App_Version")}: ${syncRuntimeInfo.appVersion}
        |${copywriter.getText("User_Name")}: ${syncRuntimeInfo.userName}
        |${copywriter.getText("Platform")}: ${syncRuntimeInfo.platformName} ${syncRuntimeInfo.platformVersion}
        |${copywriter.getText("State")}: ${syncRuntimeInfo.connectState}
    """.trimMargin()
}