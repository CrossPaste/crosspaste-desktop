package com.crosspaste.ui.base

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crosspaste.i18n.GlobalCopywriter

interface ExpandViewProvider {

    val copywriter: GlobalCopywriter

    @Composable
    fun ExpandView(
        defaultExpand: Boolean = false,
        horizontalPadding: Dp = 16.dp,
        barBackgroundColor: Color = MaterialTheme.colorScheme.secondary,
        onBarBackgroundColor: Color = MaterialTheme.colorScheme.onSecondary,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
        barContent: @Composable (Float) -> Unit,
        content: @Composable () -> Unit,
    )

    @Composable
    fun ExpandBarView(
        title: String,
        iconScale: Float,
        onBarBackgroundColor: Color = MaterialTheme.colorScheme.onSecondary,
        icon: @Composable () -> Painter? = { null },
    )
}
