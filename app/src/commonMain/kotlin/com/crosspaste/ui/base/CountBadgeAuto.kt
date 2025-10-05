package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.large

@Composable
fun CountBadgeAuto(
    count: Long,
    minSize: Dp = large,
    bg: Color = Color.Red,
    fg: Color = Color.White,
    textStyle: TextStyle = AppUIFont.tipsTextStyle,
) {
    val label = remember(count) { if (count > 99) "99+" else count.toString() }
    val isSingle = label.length == 1
    val shape = if (isSingle) CircleShape else RoundedCornerShape(percent = 50)

    Box(
        modifier =
            Modifier
                .heightIn(min = minSize)
                .then(if (isSingle) Modifier.widthIn(min = minSize) else Modifier)
                .clip(shape)
                .background(bg)
                .padding(horizontal = if (isSingle) 0.dp else minSize * 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = textStyle.copy(color = fg),
        )
    }
}
