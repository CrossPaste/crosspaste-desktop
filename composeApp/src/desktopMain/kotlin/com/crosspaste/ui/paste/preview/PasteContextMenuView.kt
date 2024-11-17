package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PasteContextMenuView(
    modifier: Modifier = Modifier,
    items: () -> List<ContextMenuItem>,
    content: @Composable () -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        ContextMenuArea(
            items = items,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) { // 内部内容使用 fillMaxWidth
                content()
            }
        }
    }
}
