package com.crosspaste.ui.base

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUISize.medium

interface ExpandViewProvider {

    val copywriter: GlobalCopywriter

    @Composable
    fun ExpandView(
        defaultExpand: Boolean = false,
        horizontalPadding: Dp = medium,
        barBackgroundColor: Color = MaterialTheme.colorScheme.secondary,
        onBarBackgroundColor: Color = MaterialTheme.colorScheme.onSecondary,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
        barContent: @Composable RowScope.(Float) -> Unit,
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
