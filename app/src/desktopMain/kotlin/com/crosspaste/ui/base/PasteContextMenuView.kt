package com.crosspaste.ui.base

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PasteContextMenuView(
    items: () -> List<ContextMenuItem>,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.wrapContentSize()) {
        ContextMenuArea(
            items = items,
        ) {
            content()
        }
    }
}
