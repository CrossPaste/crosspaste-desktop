package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crosspaste.db.paste.PasteData

@Composable
fun PasteHtmlOrRtfIcon(
    pasteData: PasteData,
    iconColor: Color,
    size: Dp = 20.dp,
) {
    pasteData.source?.let {
        AppSourceIcon(
            pasteData = pasteData,
            source = it,
            iconColor = iconColor,
            size = size,
        )
    } ?: run {
        Icon(
            painter = htmlOrRtf(),
            contentDescription = "Paste Icon",
            modifier = Modifier.size(size),
            tint = iconColor,
        )
    }
}
