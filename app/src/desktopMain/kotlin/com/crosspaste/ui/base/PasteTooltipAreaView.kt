package com.crosspaste.ui.base

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.zero

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PasteTooltipAreaView(
    modifier: Modifier = Modifier,
    text: String,
    delayMillis: Int = 500,
    computeTooltipPlacement: @Composable () -> TooltipPlacement = {
        TooltipPlacement.CursorPoint(
            offset = DpOffset(zero, medium),
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
                        .shadow(tiny2X),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .clip(tiny2XRoundedCornerShape),
                    tonalElevation = tiny2X,
                    shadowElevation = tiny2X,
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(tiny3X),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                lineHeight = TextUnit.Unspecified,
                            ),
                    )
                }
            }
        },
    ) {
        content()
    }
}
