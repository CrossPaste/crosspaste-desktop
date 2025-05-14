package com.crosspaste.ui.base

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
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
        title: String,
        icon: @Composable () -> Painter? = { null },
        defaultExpand: Boolean = false,
        horizontalPadding: Dp = 16.dp,
        titleBackgroundColor: Color = MaterialTheme.colorScheme.secondary,
        onTitleBackgroundColor: Color = MaterialTheme.colorScheme.onSecondary,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
        barContent: @Composable RowScope.(Float) -> Unit = { iconScale ->
            ExpandBarView(title, iconScale, titleBackgroundColor, onTitleBackgroundColor, icon)
        },
        content: @Composable ColumnScope.() -> Unit,
    )

    @Composable
    fun ExpandBarView(
        title: String,
        iconScale: Float,
        titleBackgroundColor: Color = MaterialTheme.colorScheme.secondary,
        onTitleBackgroundColor: Color = MaterialTheme.colorScheme.onSecondary,
        icon: @Composable () -> Painter? = { null },
    )
}
