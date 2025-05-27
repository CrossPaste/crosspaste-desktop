package com.crosspaste.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

object AppUIColors {

    val appBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface

    val aboutBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHigh

    val deviceBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val dialogBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val importOrExportBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val qrBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val recommendedBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val searchBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface

    val searchFootBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val settingsBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val settingsTitleBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background

    val shortcutBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val tabsBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primaryContainer

    val tabSelectedTextColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onPrimaryContainer

    val tabUnselectedTextColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
}
