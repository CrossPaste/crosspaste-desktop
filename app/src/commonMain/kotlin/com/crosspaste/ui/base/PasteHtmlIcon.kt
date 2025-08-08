package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.crosspaste.paste.PasteData
import com.crosspaste.ui.theme.AppUISize.large2X

@Composable
fun PasteHtmlIcon(
    pasteData: PasteData,
    iconColor: Color,
    size: Dp = large2X,
) {
    pasteData.source?.let {
        AppSourceIcon(
            pasteData = pasteData,
            source = it,
            size = size,
        ) {
            DefaultHtmlIcon(
                iconColor = iconColor,
                size = size,
            )
        }
    } ?: run {
        DefaultHtmlIcon(
            iconColor = iconColor,
            size = size,
        )
    }
}

@Composable
fun DefaultHtmlIcon(
    contentDescription: String? = null,
    iconColor: Color,
    size: Dp = large2X,
) {
    Icon(
        painter = html(),
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
        tint = iconColor,
    )
}
