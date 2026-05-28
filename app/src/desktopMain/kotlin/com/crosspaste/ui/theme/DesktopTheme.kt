package com.crosspaste.ui.theme

import androidx.compose.runtime.Composable
import com.crosspaste.ui.theme.CrossPasteTheme.Theme

@Composable
fun DesktopTheme(content: @Composable () -> Unit) {
    Theme {
        content()
    }
}
