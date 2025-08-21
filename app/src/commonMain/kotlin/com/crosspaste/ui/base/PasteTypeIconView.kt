package com.crosspaste.ui.base

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.large2X

@Composable
fun PasteDataScope.PasteTypeIconView(
    tint: Color? = null,
    background: Color,
    size: Dp = large2X,
) {
    val pasteType = pasteData.getType()

    val iconColor = tint ?: MaterialTheme.colorScheme.contentColorFor(background)

    if (pasteType.isUrl()) {
        PasteUrlIcon(
            iconColor = iconColor,
            size = size,
        )
    } else if (pasteType.isFile()) {
        PasteFileIcon(
            iconColor = iconColor,
            size = size,
        )
    } else if (pasteType.isHtml()) {
        PasteHtmlIcon(
            iconColor = iconColor,
            size = size,
        )
    } else if (pasteType.isRtf()) {
        PasteRtfIcon(
            iconColor = iconColor,
            size = size,
        )
    } else {
        DefaultPasteTypeIcon(
            iconColor = iconColor,
            size = size,
        )
    }
}
