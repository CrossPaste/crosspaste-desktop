package com.crosspaste.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.LocalThemeState

object AppUIColors {

    val appBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface

    /**
     * Ground behind the main content area (pasteboard, devices, settings). Light theme
     * uses a slightly tinted grey so white section cards stand out; dark theme keeps
     * the plain surface because M3 dark cards are lighter than their ground.
     */
    val contentBackground: Color
        @Composable @ReadOnlyComposable
        get() =
            if (LocalThemeState.current.isCurrentThemeDark) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }

    val sectionCardBackground: Color
        @Composable @ReadOnlyComposable
        get() =
            if (LocalThemeState.current.isCurrentThemeDark) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }

    /**
     * Desktop navigation sidebar. It sits one level above [contentBackground] in both
     * themes: white on the light grey ground, a raised container on the dark ground.
     */
    val sidebarBackground: Color
        @Composable @ReadOnlyComposable
        get() =
            if (LocalThemeState.current.isCurrentThemeDark) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }

    val sectionCardBorder: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outlineVariant

    val sectionTitleColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    val generalBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val lightBorderColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)

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

    val selectedMenuBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primaryContainer

    val topBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerLowest
}
