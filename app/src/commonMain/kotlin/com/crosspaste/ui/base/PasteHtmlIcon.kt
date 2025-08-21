package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.large2X

@Composable
fun PasteDataScope.PasteHtmlIcon(
    iconColor: Color,
    size: Dp = large2X,
) {
    AppSourceIcon(
        size = size,
    ) {
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
