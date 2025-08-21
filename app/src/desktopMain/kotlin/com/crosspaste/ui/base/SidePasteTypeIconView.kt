package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.paste.PasteDataScope

@Composable
fun PasteDataScope.SidePasteTypeIconView(
    modifier: Modifier = Modifier,
    tint: Color,
) {
    pasteData.source?.let {
        SideAppSourceIcon(
            modifier = modifier,
        ) {
            SideDefaultPasteTypeIcon(
                tint = tint,
            )
        }
    } ?: run {
        SideDefaultPasteTypeIcon(
            tint = tint,
        )
    }
}
