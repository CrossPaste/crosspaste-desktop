package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.db.paste.PasteData

@Composable
fun SidePasteTypeIconView(
    modifier: Modifier = Modifier,
    pasteData: PasteData,
    tint: Color,
) {
    pasteData.source?.let {
        SideAppSourceIcon(
            modifier = modifier,
            pasteData = pasteData,
        ) {
            SideDefaultPasteTypeIcon(
                pasteData = pasteData,
                tint = tint,
            )
        }
    } ?: run {
        SideDefaultPasteTypeIcon(
            pasteData = pasteData,
            tint = tint,
        )
    }
}
