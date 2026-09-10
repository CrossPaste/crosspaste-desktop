package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.crosspaste.ui.base.PlatformIcon
import com.crosspaste.ui.theme.AppUISize
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge

/**
 * [nameTrailing] sits right after the device name on the title line. The
 * marker is capped at half the line, so the name always keeps at least the
 * other half: a short marker leaves the name all the room it needs, a long
 * translation at a large font size ellipsizes instead of squeezing the name
 * to nothing. Use it for a short inline marker (e.g. "this device") that
 * should not compete with [trailingContent] for the row's end.
 */
@Composable
fun PlatformScope.DeviceRowContent(
    style: DeviceStyle,
    onClick: (() -> Unit)? = null,
    iconTint: Color? = null,
    nameTrailing: @Composable (() -> Unit)? = null,
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
                        .background(style.iconContainerColor, small2XRoundedCornerShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = PlatformIcon(platform),
                    contentDescription = null,
                    modifier = Modifier.size(xLarge),
                    tint = iconTint ?: SyncStateColor(),
                )
            }

            // Main Content (Weight 1 helps it take available space)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                // The Row measures the unweighted marker before the weighted
                // name, so cap the marker at half the line to keep the name
                // from being starved on narrow rows with large fonts.
                BoxWithConstraints {
                    val markerMaxWidth = maxWidth / 2
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(tiny),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = getDeviceDisplayName(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = style.nameMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            color = style.titleColor,
                        )
                        nameTrailing?.let {
                            Box(modifier = Modifier.widthIn(max = markerMaxWidth)) {
                                it()
                            }
                        }
                    }
                }

                Text(
                    text = "${platform.displayName()} ${platform.version}",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = style.subtitleColor,
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

/**
 * A short plain-text marker for [DeviceRowContent]'s `nameTrailing` slot,
 * e.g. "This device": bold in the primary color, so it reads as a small
 * caption on the title rather than as a status tag.
 */
@Composable
fun DeviceNameMarker(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}
