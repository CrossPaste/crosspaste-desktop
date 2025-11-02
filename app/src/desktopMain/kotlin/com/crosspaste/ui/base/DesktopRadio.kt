package com.crosspaste.ui.base

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DesktopRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    size: Dp = 20.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val selectableModifier =
        if (onClick != null) {
            Modifier.selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
            )
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .then(selectableModifier)
                .size(size),
        contentAlignment = Alignment.Center,
    ) {
        RadioButtonIndicator(
            selected = selected,
            enabled = enabled,
            colors = colors,
            size = size,
        )
    }
}

@Composable
private fun RadioButtonIndicator(
    selected: Boolean,
    enabled: Boolean,
    colors: RadioButtonColors,
    size: Dp,
) {
    val borderColor by animateColorAsState(
        targetValue = colors.borderColor(enabled, selected),
        animationSpec = tween(durationMillis = 100),
        label = "borderColor",
    )

    val dotColor by animateColorAsState(
        targetValue = colors.dotColor(enabled),
        animationSpec = tween(durationMillis = 100),
        label = "dotColor",
    )

    val dotSize by animateDpAsState(
        targetValue = if (selected) size * 0.5f else 0.dp,
        animationSpec = tween(durationMillis = 100),
        label = "dotSize",
    )

    Canvas(
        modifier = Modifier.size(size),
    ) {
        drawCircle(
            color = borderColor,
            radius = size.toPx() / 2,
            style =
                androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (size.toPx() * 0.1f).coerceAtLeast(1.dp.toPx()),
                ),
        )

        if (dotSize > 0.dp) {
            drawCircle(
                color = dotColor,
                radius = dotSize.toPx() / 2,
            )
        }
    }
}

fun RadioButtonColors.borderColor(
    enabled: Boolean,
    selected: Boolean,
): Color =
    when {
        !enabled && selected -> disabledSelectedColor
        !enabled && !selected -> disabledUnselectedColor
        selected -> selectedColor
        else -> unselectedColor
    }

fun RadioButtonColors.dotColor(enabled: Boolean): Color =
    when {
        !enabled -> disabledSelectedColor
        else -> selectedColor
    }
