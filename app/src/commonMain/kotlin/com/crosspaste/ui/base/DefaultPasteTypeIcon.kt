package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.crosspaste.db.paste.PasteData
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.xxxxLarge

@Composable
fun DefaultPasteTypeIcon(
    modifier: Modifier = Modifier,
    pasteData: PasteData,
    iconColor: Color,
    size: Dp,
) {
    val type by remember(pasteData.id) { mutableStateOf(pasteData.getType()) }
    Icon(
        painter = type.IconPainter(),
        contentDescription = "Paste Icon",
        modifier = modifier.size(size),
        tint = iconColor,
    )
}

@Composable
fun SideDefaultPasteTypeIcon(
    modifier: Modifier = Modifier,
    pasteData: PasteData,
    tint: Color,
) {
    val type by remember(pasteData.id) { mutableStateOf(pasteData.getType()) }
    Box(
        modifier =
            modifier.fillMaxHeight()
                .wrapContentWidth()
                .clip(RoundedCornerShape(topStart = tiny2X, bottomStart = tiny2X))
                .background(type.getIconBackgroundColor()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = type.IconPainter(),
            contentDescription = "Paste Icon",
            modifier =
                modifier.padding(start = tiny, end = tiny)
                    .size(xxxxLarge),
            tint = tint,
        )
    }
}
