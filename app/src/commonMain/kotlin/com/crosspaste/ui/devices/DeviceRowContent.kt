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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.PlatformIcon
import com.crosspaste.ui.base.cardPressRipple
import com.crosspaste.ui.theme.AppUISize
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.AppUISize.zero

/**
 * [nameTrailing] sits right after the device name on the title line. The
 * marker is capped at half the line, so the name always keeps at least the
 * other half: a short marker leaves the name all the room it needs, a long
 * translation at a large font size ellipsizes instead of squeezing the name
 * to nothing. Use it for a short inline marker (e.g. "this device") that
 * should not compete with [trailingContent] for the row's end.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        CardDefaults.cardColors(
            containerColor = style.containerColor,
            contentColor = style.contentColor,
            disabledContainerColor = style.containerColor,
            disabledContentColor = style.contentColor,
        )

    val isDark = LocalThemeState.current.isCurrentThemeDark
    val parentRippleConfiguration = LocalRippleConfiguration.current
    val cardRippleConfiguration = if (isDark) parentRippleConfiguration else cardPressRipple

    // In light theme, hover lifts the card with a shadow instead of tinting it: the light
    // card is pure white, so a grey overlay would blur its edge into the grey ground. Tonal
    // elevation is disabled in light theme because white equals surface and would be tinted
    // with primary. In dark theme, shadows on a dark ground are invisible, so tonal elevation
    // and standard hover ripple provide the required interactive feedback.
    CompositionLocalProvider(
        LocalTonalElevationEnabled provides isDark,
        LocalRippleConfiguration provides cardRippleConfiguration,
    ) {
        Card(
            onClick = onClick ?: {},
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = style.shape,
            colors = colors,
            elevation =
                CardDefaults.cardElevation(
                    defaultElevation = zero,
                    pressedElevation = zero,
                    focusedElevation = zero,
                    hoveredElevation = tiny4X,
                    disabledElevation = zero,
                ),
            interactionSource = interactionSource,
        ) {
            CompositionLocalProvider(
                LocalRippleConfiguration provides parentRippleConfiguration,
            ) {
                DeviceRowBody(style, iconTint, nameTrailing, trailingContent)
            }
        }
    }
}

@Composable
private fun PlatformScope.DeviceRowBody(
    style: DeviceStyle,
    iconTint: Color?,
    nameTrailing: @Composable (() -> Unit)?,
    trailingContent: @Composable (() -> Unit)?,
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
