package com.crosspaste.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.crosspaste.ui.ThemeDetector
import org.koin.compose.koinInject

object CrossPasteTheme {
    val LightColorScheme =
        lightColorScheme(
            primary = primaryLight,
            onPrimary = onPrimaryLight,
            primaryContainer = primaryContainerLight,
            onPrimaryContainer = onPrimaryContainerLight,
            secondary = secondaryLight,
            onSecondary = onSecondaryLight,
            secondaryContainer = secondaryContainerLight,
            onSecondaryContainer = onSecondaryContainerLight,
            tertiary = tertiaryLight,
            onTertiary = onTertiaryLight,
            tertiaryContainer = tertiaryContainerLight,
            onTertiaryContainer = onTertiaryContainerLight,
            error = errorLight,
            onError = onErrorLight,
            errorContainer = errorContainerLight,
            onErrorContainer = onErrorContainerLight,
            background = backgroundLight,
            onBackground = onBackgroundLight,
            surface = surfaceLight,
            onSurface = onSurfaceLight,
            surfaceVariant = surfaceVariantLight,
            onSurfaceVariant = onSurfaceVariantLight,
            outline = outlineLight,
            outlineVariant = outlineVariantLight,
            scrim = scrimLight,
            inverseSurface = inverseSurfaceLight,
            inverseOnSurface = inverseOnSurfaceLight,
            inversePrimary = inversePrimaryLight,
            surfaceDim = surfaceDimLight,
            surfaceBright = surfaceBrightLight,
            surfaceContainerLowest = surfaceContainerLowestLight,
            surfaceContainerLow = surfaceContainerLowLight,
            surfaceContainer = surfaceContainerLight,
            surfaceContainerHigh = surfaceContainerHighLight,
            surfaceContainerHighest = surfaceContainerHighestLight,
        )

    val DarkColorScheme =
        darkColorScheme(
            primary = primaryDark,
            onPrimary = onPrimaryDark,
            primaryContainer = primaryContainerDark,
            onPrimaryContainer = onPrimaryContainerDark,
            secondary = secondaryDark,
            onSecondary = onSecondaryDark,
            secondaryContainer = secondaryContainerDark,
            onSecondaryContainer = onSecondaryContainerDark,
            tertiary = tertiaryDark,
            onTertiary = onTertiaryDark,
            tertiaryContainer = tertiaryContainerDark,
            onTertiaryContainer = onTertiaryContainerDark,
            error = errorDark,
            onError = onErrorDark,
            errorContainer = errorContainerDark,
            onErrorContainer = onErrorContainerDark,
            background = backgroundDark,
            onBackground = onBackgroundDark,
            surface = surfaceDark,
            onSurface = onSurfaceDark,
            surfaceVariant = surfaceVariantDark,
            onSurfaceVariant = onSurfaceVariantDark,
            outline = outlineDark,
            outlineVariant = outlineVariantDark,
            scrim = scrimDark,
            inverseSurface = inverseSurfaceDark,
            inverseOnSurface = inverseOnSurfaceDark,
            inversePrimary = inversePrimaryDark,
            surfaceDim = surfaceDimDark,
            surfaceBright = surfaceBrightDark,
            surfaceContainerLowest = surfaceContainerLowestDark,
            surfaceContainerLow = surfaceContainerLowDark,
            surfaceContainer = surfaceContainerDark,
            surfaceContainerHigh = surfaceContainerHighDark,
            surfaceContainerHighest = surfaceContainerHighestDark,
        )

    @Composable
    fun Theme(content: @Composable () -> Unit) {
        val themeDetector = koinInject<ThemeDetector>()

        val colorScheme = themeDetector.getCurrentColorScheme()

        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }

    fun Color.darken(amount: Float): Color {
        return copy(
            red = (red * (1 - amount)).coerceAtLeast(0f),
            green = (green * (1 - amount)).coerceAtLeast(0f),
            blue = (blue * (1 - amount)).coerceAtLeast(0f),
        )
    }

    @Composable
    fun ColorScheme.favoriteColor(): Color {
        return if (isLight()) {
            Color(0xFFFFAA00)
        } else {
            Color(0xFFFFCE34)
        }
    }

    @Composable
    fun ColorScheme.selectColor(): Color {
        return if (isLight()) {
            Color(0xFFEBF6FF)
        } else {
            Color(0xFF2F446F)
        }
    }

    @Composable
    fun ColorScheme.isLight(): Boolean {
        return surface.luminance() > 0.5
    }

    fun connectedColor(): Color {
        return Color(0xFF95EC69)
    }

    fun connectingColor(): Color {
        return Color(0xFFE6C44D)
    }

    fun disconnectedColor(): Color {
        return Color(0xFFFF6969)
    }

    fun unmatchedColor(): Color {
        return Color(0xFF9A69EC)
    }

    fun unverifiedColor(): Color {
        return Color(0xFF69A9EC)
    }

    fun grantPermissionColor(): Color {
        return Color(0xFF95EC69)
    }
}
