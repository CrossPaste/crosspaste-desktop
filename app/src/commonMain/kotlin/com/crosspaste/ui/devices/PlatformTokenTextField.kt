package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle

@Composable
expect fun PlatformTokenTextField(
    value: String,
    enabled: Boolean,
    index: Int,
    tokenCount: Int,
    onValueChange: (String) -> Unit,
    onBackspaceWhenEmpty: () -> Unit,
    modifier: Modifier,
    textStyle: TextStyle,
    cursorBrush: Brush,
)
