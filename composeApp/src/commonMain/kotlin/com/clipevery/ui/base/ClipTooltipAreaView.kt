package com.clipevery.ui.base

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipTooltipAreaView(
    text: String,
    content: @Composable () -> Unit,
) {
    TooltipArea(
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
                        style =
                            TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                            ),
                    )
                }
            }
        },
    ) {
        content()
    }
}
