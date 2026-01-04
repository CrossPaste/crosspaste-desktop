package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.ui.base.PlatformIcon
import com.crosspaste.ui.theme.AppUISize
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge

@Composable
fun PlatformScope.DeviceRowContent(
    style: DeviceStyle,
    onClick: (() -> Unit)? = null,
    tagContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val enabled = style.isClickable && onClick != null

    val colors =
        if (enabled) {
            CardDefaults.cardColors(
                containerColor = style.containerColor,
                contentColor = style.contentColor,
            )
        } else {
            CardDefaults.cardColors(
                disabledContainerColor = style.containerColor,
                disabledContentColor = style.contentColor,
            )
        }

    Card(
        onClick = onClick ?: {},
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = style.shape,
        colors = colors,
        interactionSource = onClick?.let { interactionSource },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(style.paddingValues),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppUISize.medium),
        ) {
            // Leading Icon
            Box(
                modifier =
                    Modifier
                        .size(xxxxLarge)
                        .background(style.iconContentColor, small2XRoundedCornerShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = PlatformIcon(platform),
                    contentDescription = null,
                    modifier = Modifier.size(xLarge),
                    tint = style.contentColor,
                )
            }

            // Main Content (Weight 1 helps it take available space)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(tiny),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = "${platform.name} ${platform.version}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = style.contentColor,
                    )
                    tagContent?.invoke()
                }

                Text(
                    text = getDeviceDisplayName(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.SansSerif,
                    color = style.contentColor.copy(alpha = 0.7f),
                )
            }

            // Trailing Content
            trailingContent?.let {
                Box(contentAlignment = Alignment.Center) {
                    it()
                }
            }
        }
    }
}
