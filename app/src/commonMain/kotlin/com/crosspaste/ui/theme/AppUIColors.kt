package com.crosspaste.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

object AppUIColors {

    val appBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface

    val errorColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.error

    val errorContainerColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.errorContainer

    val expandBarBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondary

    val generalBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val lightBorderColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)

    val mediumBorderColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)

    val darkBorderColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline

    val importantColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    val menuBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceBright

    val pasteBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background

    val pasteShimmerColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondaryContainer

    val segmentedButtonColors: SegmentedButtonColors
        @Composable
        get() =
            SegmentedButtonDefaults.colors(
                activeContainerColor = MaterialTheme.colorScheme.primary,
                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                inactiveContentColor = MaterialTheme.colorScheme.onSurface,
            )

    val selectedColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    val selectedMenuBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primaryContainer

    val topBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerLowest

    val urlColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary
}
