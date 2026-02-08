package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xxxLarge

@Composable
fun BottomGradient(
    text: String,
    backgroundColor: Color = AppUIColors.pasteBackground,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(xxxLarge)
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                backgroundColor.copy(alpha = 0.2f),
                                backgroundColor.copy(alpha = 0.8f),
                                backgroundColor.copy(alpha = 0.9f),
                                backgroundColor,
                                backgroundColor,
                                backgroundColor,
                            ),
                    ),
                ).padding(horizontal = medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style =
                AppUIFont.mediumBodyTextStyle.copy(
                    color = Color(0x8A8A8A8A),
                ),
            maxLines = 1,
            softWrap = false,
        )
    }
}
