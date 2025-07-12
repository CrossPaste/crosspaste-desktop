package com.crosspaste.ui.base

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.crosspaste.db.paste.PasteData
import com.crosspaste.ui.theme.AppUISize.large2X

@Composable
fun PasteTypeIconView(
    pasteData: PasteData,
    tint: Color? = null,
    background: Color,
    size: Dp = large2X,
) {
    val pasteType = pasteData.getType()

    val iconColor = tint ?: MaterialTheme.colorScheme.contentColorFor(background)

    if (pasteType.isUrl()) {
        PasteUrlIcon(
            pasteData = pasteData,
            iconColor = iconColor,
            size = size,
        )
    } else if (pasteType.isFile()) {
        PasteFileIcon(
            pasteData = pasteData,
            iconColor = iconColor,
            size = size,
        )
    } else if (pasteType.isHtml()) {
        PasteHtmlIcon(
            pasteData = pasteData,
            iconColor = iconColor,
            size = size,
        )
    } else if (pasteType.isRtf()) {
        PasteRtfIcon(
            pasteData = pasteData,
            iconColor = iconColor,
            size = size,
        )
    } else {
        DefaultPasteTypeIcon(
            pasteData = pasteData,
            iconColor = iconColor,
            size = size,
        )
    }
}
