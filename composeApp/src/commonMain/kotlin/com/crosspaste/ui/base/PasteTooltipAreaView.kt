package com.crosspaste.ui.base

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val TOOLTIP_TEXT_STYLE =
    TextStyle(
        fontFamily = FontFamily.SansSerif,
        lineHeight = 16.sp,
        fontSize = 12.sp,
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PasteTooltipAreaView(
    modifier: Modifier = Modifier,
    text: String,
    delayMillis: Int = 500,
    computeTooltipPlacement: @Composable () -> TooltipPlacement = {
        TooltipPlacement.CursorPoint(
            offset = DpOffset(0.dp, 16.dp),
        )
    },
    content: @Composable () -> Unit,
) {
    TooltipArea(
        modifier = modifier,
        delayMillis = delayMillis,
        tooltipPlacement = computeTooltipPlacement(),
        tooltip = {
            Box(
                modifier =
                    Modifier
                        .wrapContentSize()
                        .background(Color.Transparent)
                        .shadow(5.dp),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(5.dp)),
                    elevation = 6.dp,
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(4.dp),
                        style = TOOLTIP_TEXT_STYLE,
                    )
                }
            }
        },
    ) {
        content()
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun PasteTooltipIconView(
    painter: Painter,
    text: String,
    contentDescription: String = "",
    onClick: () -> Unit,
) {
    var hoverIcon by remember { mutableStateOf(false) }

    PasteTooltipAreaView(
        modifier = Modifier.size(32.dp),
        text = text,
    ) {
        Box(
            modifier =
                Modifier.size(32.dp)
                    .onPointerEvent(
                        eventType = PointerEventType.Enter,
                        onEvent = {
                            hoverIcon = true
                        },
                    )
                    .onPointerEvent(
                        eventType = PointerEventType.Exit,
                        onEvent = {
                            hoverIcon = false
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (hoverIcon) {
                                MaterialTheme.colors.surface.copy(0.64f)
                            } else {
                                Color.Transparent
                            },
                        ).onClick {
                            onClick()
                        },
            ) {}

            Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colors.onBackground,
            )
        }
    }
}
