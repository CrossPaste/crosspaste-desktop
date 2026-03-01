package com.crosspaste.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

interface ScreenProvider : NavigationManager {

    @Composable
    fun screen()

    @Composable
    fun ScreenLayout(
        horizontal: Dp,
        top: Dp,
        bottom: Dp,
        content: @Composable BoxScope.() -> Unit,
    )
}
