package com.crosspaste.ui.base

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crosspaste.db.paste.PasteData

@Composable
fun PasteTypeIconView(
    pasteData: PasteData,
    backgroundColor: Color,
    size: Dp = 20.dp,
) {
    val pasteType = pasteData.getType()

    val iconColor = MaterialTheme.colorScheme.contentColorFor(backgroundColor)

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
    } else if (pasteType.isHtml() || pasteType.isRtf()) {
        PasteHtmlOrRtfIcon(
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
