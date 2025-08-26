package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.app.AppSize
import org.koin.compose.koinInject

@Composable
fun PlatformScope.HoverableDeviceBarView(content: @Composable (Color) -> Unit) {
    val appSize = koinInject<AppSize>()

    var hover by remember { mutableStateOf(false) }

    val background =
        if (hover) {
            selectedPlatformBackground
        } else {
            platformBackground
        }

    val onBackground = MaterialTheme.colorScheme.contentColorFor(background)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(appSize.deviceHeight)
                .background(background)
                .hoverModifier(
                    onEnter = {
                        hover = true
                    },
                    onExit = {
                        hover = false
                    },
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceBaseInfoView(
            platform = platform,
            deviceDisplayName = getDeviceDisplayName(),
            onBackground = onBackground,
        )

        Row(
            modifier = Modifier.wrapContentWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content(background)
        }
    }
}
