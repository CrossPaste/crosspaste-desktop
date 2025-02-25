package com.crosspaste.ui.base

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
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
