package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.crosspaste.i18n.GlobalCopywriter
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun GeneralIconButton(
    imageVector: ImageVector,
    desc: String?,
    colors: IconButtonColors,
    modifier: Modifier,
    iconModifier: Modifier,
    iconColor: Color,
    buttonSize: Dp,
    iconSize: Dp,
    shape: Shape,
    onClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()

    @Composable
    fun IconButtonContent() {
        IconButton(
            onClick = onClick,
            colors = colors,
            modifier =
                modifier
                    .size(buttonSize),
            shape = shape,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = desc,
                modifier = iconModifier.size(iconSize),
                tint = iconColor,
            )
        }
    }

    if (!desc.isNullOrBlank()) {
        val tooltipState = rememberTooltipState()
        val positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Above)

        TooltipBox(
            positionProvider = positionProvider,
            tooltip = {
                PlainTooltip {
                    Text(copywriter.getText(desc))
                }
            },
            state = tooltipState,
        ) {
            IconButtonContent()
        }
    } else {
        IconButtonContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun GeneralIconButton(
    painter: Painter,
    desc: String?,
    colors: IconButtonColors,
    modifier: Modifier,
    iconModifier: Modifier,
    iconColor: Color,
    buttonSize: Dp,
    iconSize: Dp,
    shape: Shape,
    onClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()

    @Composable
    fun IconButtonContent() {
        IconButton(
            onClick = onClick,
            colors = colors,
            modifier =
                modifier
                    .size(buttonSize),
            shape = shape,
        ) {
            Icon(
                painter = painter,
                contentDescription = desc,
                modifier = iconModifier.size(iconSize),
                tint = iconColor,
            )
        }
    }

    if (!desc.isNullOrBlank()) {
        val tooltipState = rememberTooltipState()
        val positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Above)

        TooltipBox(
            positionProvider = positionProvider,
            tooltip = {
                PlainTooltip {
                    Text(copywriter.getText(desc))
                }
            },
            state = tooltipState,
        ) {
            IconButtonContent()
        }
    } else {
        IconButtonContent()
    }
}
