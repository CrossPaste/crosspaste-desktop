package com.crosspaste.ui.devices

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.platform.Platform
import com.crosspaste.ui.base.PlatformIcon
import com.crosspaste.ui.theme.AppUIFont.platformNameTextStyle
import com.crosspaste.ui.theme.AppUIFont.platformTextStyle
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.xxxLarge

@Composable
fun RowScope.DeviceBaseInfoView(
    platform: Platform,
    deviceDisplayName: String,
    onBackground: Color,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier =
            Modifier
                .weight(1f, fill = false)
                .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.padding(horizontal = small2X).size(xxxLarge),
            painter = PlatformIcon(platform),
            contentDescription = "OS Icon",
            tint = onBackground,
        )

        Column(
            modifier =
                Modifier
                    .height(xxxLarge)
                    .padding(bottom = tiny4X),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = platform.name,
                    maxLines = 1,
                    style =
                        platformNameTextStyle.copy(
                            color = onBackground,
                        ),
                )
                Spacer(modifier = Modifier.width(tiny))
                Text(
                    text = platform.version,
                    maxLines = 1,
                    style =
                        platformTextStyle.copy(
                            color = onBackground,
                        ),
                )
            }

            Text(
                text = deviceDisplayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                    platformTextStyle.copy(
                        color = onBackground.copy(alpha = 0.8f),
                    ),
            )
        }
    }
}
