package com.crosspaste.ui.base

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium

interface ExpandViewProvider {

    val copywriter: GlobalCopywriter

    @Composable
    fun ExpandView(
        defaultExpand: Boolean = false,
        horizontalPadding: Dp = medium,
        barBackground: Color = AppUIColors.expandBarBackground,
        onBarBackground: Color =
            MaterialTheme.colorScheme.contentColorFor(
                AppUIColors.expandBarBackground,
            ),
        backgroundColor: Color = AppUIColors.generalBackground,
        barContent: @Composable RowScope.(Float) -> Unit,
        content: @Composable () -> Unit,
    )

    @Composable
    fun ExpandBarView(
        title: String,
        iconScale: Float,
        onBarBackground: Color =
            MaterialTheme.colorScheme.contentColorFor(
                AppUIColors.expandBarBackground,
            ),
        icon: @Composable () -> Painter? = { null },
    )
}
