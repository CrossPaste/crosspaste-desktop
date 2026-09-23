package com.crosspaste.ui.devices

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.PlatformIcon
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

    val isDark = LocalThemeState.current.isCurrentThemeDark

    // In light theme, hover lifts the card with a shadow instead of tinting it: the light
    // card is pure white, so a grey overlay would blur its edge into the grey ground. Tonal
    // elevation is disabled in light theme because white equals surface and would be tinted
    // with primary. In dark theme, shadows on a dark ground are invisible, so tonal elevation
    // and standard hover ripple provide the required interactive feedback.
    //
    // The clickable sits inside the Surface rather than on it so the ripple is clipped to the
    // card shape; the Surface fills its content, so the two share the same bounds.
    CompositionLocalProvider(LocalTonalElevationEnabled provides isDark) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = style.shape,
            color = style.containerColor,
            contentColor = style.contentColor,
            shadowElevation = hoverShadowElevation(enabled, interactionSource),
        ) {
            Box(
                modifier =
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = cardRipple(isDark),
                        enabled = enabled,
                        onClick = onClick ?: {},
                    ),
            ) {
                DeviceRowBody(style, iconTint, nameTrailing, trailingContent)
            }
        }
    }
}

/**
 * Ripple for the device card. In light theme a single white card lies on the light grey
 * content ground, and the default ripple overlays onSurface grey, which sinks a pressed white
 * card into the ground (about #F0F0F0 on #F6F7F8). Tinting with primary instead gives a light
 * blue press that stays clearly separate from the ground and matches the app's blue action
 * language. Hover is disabled there: desktop lifts the card with a shadow instead, and touch
 * has no hover. Dark theme keeps the standard ripple.
 */
@Composable
private fun cardRipple(isDark: Boolean): IndicationNodeFactory =
    if (isDark) {
        ripple()
    } else {
        ripple(
            color = MaterialTheme.colorScheme.primary,
            enableHoverIndication = false,
        )
    }

/**
 * Shadow that lifts the card while hovered, mirroring Material 3 Card's hovered elevation
 * with the same 120ms tweens. Pressing drops the card back down, and a disabled card never
 * lifts.
 */
@Composable
private fun hoverShadowElevation(
    enabled: Boolean,
    interactionSource: InteractionSource,
): Dp {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val lifted = enabled && hovered && !pressed
    val elevation by
        animateDpAsState(
            targetValue = if (lifted) tiny4X else zero,
            animationSpec = if (lifted) hoverInSpec else hoverOutSpec,
        )
    return elevation
}

private val hoverInSpec = tween<Dp>(durationMillis = 120, easing = FastOutSlowInEasing)
private val hoverOutSpec =
    tween<Dp>(durationMillis = 120, easing = CubicBezierEasing(0.40f, 0.00f, 0.60f, 1.00f))

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
            // from being starved on narrow rows with large fonts. The cap
            // is a layout modifier, not BoxWithConstraints: hosts that
            // measure the row intrinsically (the mobile swipeable row uses
            // height(IntrinsicSize.Min)) cannot query a SubcomposeLayout.
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
                    Box(modifier = Modifier.halfWidthCap()) {
                        it()
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
 * Caps the content at half the width offered by the parent. Unlike
 * `BoxWithConstraints` it takes part in intrinsic measurement, which the
 * hosts of [DeviceRowContent] rely on.
 */
private fun Modifier.halfWidthCap(): Modifier =
    layout { measurable, constraints ->
        val capped =
            if (constraints.hasBoundedWidth) {
                val maxWidth = constraints.maxWidth / 2
                constraints.copy(
                    minWidth = constraints.minWidth.coerceAtMost(maxWidth),
                    maxWidth = maxWidth,
                )
            } else {
                constraints
            }
        val placeable = measurable.measure(capped)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
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
