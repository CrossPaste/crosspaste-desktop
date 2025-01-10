package com.crosspaste.ui.base

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

interface BaseViewProvider {

    @Composable
    fun ExpandView(
        title: String,
        icon: @Composable () -> Painter? = { null },
        defaultExpand: Boolean = false,
        horizontalPadding: Dp = 16.dp,
        titleBackgroundColor: Color = MaterialTheme.colorScheme.secondary,
        onTitleBackgroundColor: Color = MaterialTheme.colorScheme.onSecondary,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
        content: @Composable () -> Unit,
    )

    @Composable
    fun CrossPasteLogoView(modifier: Modifier = Modifier)
}
