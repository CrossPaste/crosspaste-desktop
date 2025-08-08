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
fun PasteRtfIcon(
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
            DefaultRtfIcon(
                iconColor = iconColor,
                size = size,
            )
        }
    } ?: run {
        DefaultRtfIcon(
            iconColor = iconColor,
            size = size,
        )
    }
}

@Composable
fun DefaultRtfIcon(
    contentDescription: String? = null,
    iconColor: Color,
    size: Dp = large2X,
) {
    Icon(
        painter = rtf(),
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
        tint = iconColor,
    )
}
