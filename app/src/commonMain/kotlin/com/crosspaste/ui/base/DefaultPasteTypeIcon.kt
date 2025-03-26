package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.crosspaste.db.paste.PasteData

@Composable
fun DefaultPasteTypeIcon(
    pasteData: PasteData,
    iconColor: Color,
    size: Dp,
) {
    Icon(
        painter = pasteData.getType().IconPainter(),
        contentDescription = "Paste Icon",
        modifier = Modifier.size(size),
        tint = iconColor,
    )
}
