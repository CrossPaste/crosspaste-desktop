package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.platform.Platform
import com.crosspaste.realm.sync.SyncRuntimeInfo
import com.crosspaste.ui.base.android
import com.crosspaste.ui.base.ipad
import com.crosspaste.ui.base.iphone
import com.crosspaste.ui.base.linux
import com.crosspaste.ui.base.macos
import com.crosspaste.ui.base.question
import com.crosspaste.ui.base.windows

@Composable
fun PlatformPainter(syncRuntimeInfo: SyncRuntimeInfo): Painter {
    val platform =
        Platform(
            syncRuntimeInfo.platformName,
            syncRuntimeInfo.platformArch,
            syncRuntimeInfo.platformBitMode,
            syncRuntimeInfo.platformVersion,
        )

    return if (platform.isMacos()) {
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
        question()
    }
}

@Composable
fun DeviceBarView(
    modifier: Modifier = Modifier,
    syncRuntimeInfo: SyncRuntimeInfo,
    deviceViewProvider: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(0.5f),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.padding(12.dp).size(36.dp),
                painter = PlatformPainter(syncRuntimeInfo),
                contentDescription = "OS Icon",
                tint = MaterialTheme.colors.onBackground,
            )

            Column(
                modifier =
                    Modifier.height(36.dp)
                        .align(Alignment.CenterVertically)
                        .padding(bottom = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.wrapContentSize(),
                        text = syncRuntimeInfo.platformName,
                        maxLines = 1,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onBackground,
                                fontSize = 13.sp,
                            ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        modifier = Modifier.wrapContentSize(),
                        text = syncRuntimeInfo.platformVersion,
                        maxLines = 1,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colors.onBackground,
                                fontSize = 11.sp,
                            ),
                    )
                }

                Text(
                    modifier = Modifier.wrapContentSize(),
                    text = syncRuntimeInfo.getDeviceDisplayName(),
                    maxLines = 1,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                        ),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier.weight(0.5f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            deviceViewProvider()
        }
    }
}
