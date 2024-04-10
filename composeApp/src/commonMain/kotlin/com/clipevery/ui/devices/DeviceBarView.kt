package com.clipevery.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.platform.Platform
import com.clipevery.ui.base.android
import com.clipevery.ui.base.ipad
import com.clipevery.ui.base.iphone
import com.clipevery.ui.base.linux
import com.clipevery.ui.base.macos
import com.clipevery.ui.base.question
import com.clipevery.ui.base.windows

@Composable
fun DeviceBarView(
    modifier: Modifier = Modifier,
    syncRuntimeInfo: SyncRuntimeInfo,
    deviceViewProvider: @Composable () -> Unit,
) {
    val platform =
        Platform(
            syncRuntimeInfo.platformName,
            syncRuntimeInfo.platformArch,
            syncRuntimeInfo.platformBitMode,
            syncRuntimeInfo.platformVersion,
        )

    val painter: Painter =
        if (platform.isMacos()) {
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

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(modifier = Modifier.wrapContentSize()) {
            Icon(
                modifier = Modifier.padding(12.dp).size(36.dp),
                painter = painter,
                contentDescription = "OS Icon",
                tint = MaterialTheme.colors.onBackground,
            )

            Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = platform.name,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onBackground,
                                fontSize = 17.sp,
                            ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = platform.version,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colors.onBackground,
                                fontSize = 15.sp,
                            ),
                    )
                }

                Text(
                    modifier = Modifier.wrapContentSize(),
                    text = syncRuntimeInfo.noteName ?: syncRuntimeInfo.deviceName,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colors.onBackground,
                            fontSize = 15.sp,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        deviceViewProvider()
    }
}
