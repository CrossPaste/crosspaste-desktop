package com.clipevery.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.i18n.Copywriter
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.model.sync.SyncInfo
import com.clipevery.model.sync.SyncState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SyncItem(syncInfo: SyncInfo) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()

    val backgroundColor = when (syncInfo.state) {
        SyncState.ONLINE -> {
            val color = Color(154, 222, 123)
            val lightColor = Color(203, 255, 169)
            val infiniteTransition = rememberInfiniteTransition()
            val animatedColor: Color by infiniteTransition.animateColor(
                initialValue = lightColor,
                targetValue = color,
                animationSpec = infiniteRepeatable(
                    // TweenSpec for smooth transition between colors
                    animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
                    // RepeatMode.Reverse to go back and forth between the colors
                    repeatMode = RepeatMode.Reverse
                )
            )
            animatedColor
        }
        SyncState.OFFLINE -> {
            Color(238, 245, 255)
        }
        else -> {
            Color(250, 112, 112)
        }
    }

    val showHostAddress = syncInfo.state != SyncState.UNVERIFIED

    val showWarning = syncInfo.state != SyncState.ONLINE

    val platform = syncInfo.endpointInfo.platform

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
        .height(72.dp)
        .background(backgroundColor),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            modifier = Modifier.padding(10.dp).size(44.dp),
            imageVector = imageVector,
            contentDescription = "OS Icon",
            tint = Color.Unspecified
        )

        Column(modifier = Modifier.align(Alignment.CenterVertically)) {
            Row(modifier = Modifier.wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                Text(text = platform.name,
                    style = TextStyle(fontWeight = FontWeight.Bold),
                    fontSize = 25.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = platform.version,
                    style = TextStyle(fontWeight = FontWeight.Light),
                    fontSize = 15.sp)
            }

            Text(modifier = Modifier.width(150.dp),
                text = syncInfo.endpointInfo.deviceId,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.weight(2f, fill = true))

        if (showWarning) {
            Icon(
                modifier = Modifier.padding(6.dp).size(20.dp),
                imageVector = warning(),
                contentDescription = "Info Icon",
                tint = Color.Yellow
            )
        }

        if (showHostAddress) {
            Text(
                text = syncInfo.endpointInfo.hostInfo.hostAddress,
                style = TextStyle(fontWeight = FontWeight.Light),
                fontFamily = FontFamily.SansSerif,
                fontSize = 17.sp
            )
        }

        val detailInfo by remember { mutableStateOf(deviceDetailInfo(copywriter, syncInfo)) }

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
                modifier = Modifier.padding(16.dp).size(20.dp),
                imageVector = question(),
                contentDescription = "Info Icon",
                tint = Color.Unspecified
            )
        }
    }
}

fun deviceDetailInfo(copywriter: Copywriter, syncInfo: SyncInfo): String {
    return """
        |${copywriter.getText("Device_ID")}: ${syncInfo.endpointInfo.deviceId}
        |${copywriter.getText("App_Version")}: ${syncInfo.appInfo.appVersion}
        |${copywriter.getText("User_Name")}: ${syncInfo.appInfo.userName}
        |${copywriter.getText("Host_Name")}: ${syncInfo.endpointInfo.hostInfo.hostName}
        |${copywriter.getText("Host_Address")}: ${syncInfo.endpointInfo.hostInfo.hostAddress}
        |${copywriter.getText("Platform")}: ${syncInfo.endpointInfo.platform.name} ${syncInfo.endpointInfo.platform.version}
        |${copywriter.getText("State")}: ${syncInfo.state}
    """.trimMargin()
}