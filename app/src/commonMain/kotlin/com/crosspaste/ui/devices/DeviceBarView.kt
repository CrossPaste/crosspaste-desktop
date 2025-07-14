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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.ui.base.android
import com.crosspaste.ui.base.ipad
import com.crosspaste.ui.base.iphone
import com.crosspaste.ui.base.linux
import com.crosspaste.ui.base.macos
import com.crosspaste.ui.base.question
import com.crosspaste.ui.base.windows
import com.crosspaste.ui.theme.AppUIFont.platformNameTextStyle
import com.crosspaste.ui.theme.AppUIFont.platformTextStyle
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.xxxLarge

@Composable
fun PlatformPainter(syncRuntimeInfo: SyncRuntimeInfo): Painter {
    val platform = syncRuntimeInfo.platform

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
    background: Color,
    syncRuntimeInfo: SyncRuntimeInfo,
    deviceViewProvider: @Composable (Color) -> Unit,
) {
    val onBackground = MaterialTheme.colorScheme.contentColorFor(background)

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
                modifier = Modifier.padding(horizontal = small2X).size(xxxLarge),
                painter = PlatformPainter(syncRuntimeInfo),
                contentDescription = "OS Icon",
                tint = onBackground,
            )

            Column(
                modifier =
                    Modifier
                        .height(xxxLarge)
                        .align(Alignment.CenterVertically)
                        .padding(bottom = tiny4X),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.wrapContentSize(),
                        text = syncRuntimeInfo.platform.name,
                        maxLines = 1,
                        color = onBackground,
                        style = platformNameTextStyle,
                    )
                    Spacer(modifier = Modifier.width(tiny))
                    Text(
                        modifier = Modifier.wrapContentSize(),
                        text = syncRuntimeInfo.platform.version,
                        maxLines = 1,
                        color = onBackground,
                        style = platformTextStyle,
                    )
                }

                Text(
                    modifier = Modifier.wrapContentSize(),
                    text = syncRuntimeInfo.getDeviceDisplayName(),
                    maxLines = 1,
                    color = onBackground.copy(alpha = 0.8f),
                    style = platformTextStyle,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier.weight(0.5f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            deviceViewProvider(background)
        }
    }
}
