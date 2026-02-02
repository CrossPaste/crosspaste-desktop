package com.crosspaste.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object CrossPasteColor : ThemeColor {
    val primaryLight = Color(0xFF3B82F6)
    val onPrimaryLight = Color(0xFFFFFFFF)
    val primaryContainerLight = Color(0xFFDBEAFE)
    val onPrimaryContainerLight = Color(0xFF1E3A8A)
    val secondaryLight = Color(0xFF6366F1)
    val onSecondaryLight = Color(0xFFFFFFFF)
    val secondaryContainerLight = Color(0xFFE0E7FF)
    val onSecondaryContainerLight = Color(0xFF312E81)
    val tertiaryLight = Color(0xFFEC4899)
    val onTertiaryLight = Color(0xFFFFFFFF)
    val tertiaryContainerLight = Color(0xFFFCE7F3)
    val onTertiaryContainerLight = Color(0xFF831843)
    val errorLight = Color(0xFFEF4444)
    val onErrorLight = Color(0xFFFFFFFF)
    val errorContainerLight = Color(0xFFFEE2E2)
    val onErrorContainerLight = Color(0xFF7F1D1D)
    val backgroundLight = Color(0xFFFFFFFF)
    val onBackgroundLight = Color(0xFF1A1A1A)
    val surfaceLight = Color(0xFFFFFFFF)
    val onSurfaceLight = Color(0xFF1A1A1A)
    val surfaceVariantLight = Color(0xFFF3F4F6)
    val onSurfaceVariantLight = Color(0xFF6B7280)
    val outlineLight = Color(0xFF9CA3AF)
    val outlineVariantLight = Color(0xFFE5E7EB)
    val scrimLight = Color(0xFF000000)
    val inverseSurfaceLight = Color(0xFF1F2937)
    val inverseOnSurfaceLight = Color(0xFFF3F4F6)
    val inversePrimaryLight = Color(0xFF93C5FD)
    val surfaceDimLight = Color(0xFFE5E7EB)
    val surfaceBrightLight = Color(0xFFFFFFFF)
    val surfaceContainerLowestLight = Color(0xFFFFFFFF)
    val surfaceContainerLowLight = Color(0xFFFAFAFA)
    val surfaceContainerLight = Color(0xFFF6F7F8)
    val surfaceContainerHighLight = Color(0xFFF1F3F5)
    val surfaceContainerHighestLight = Color(0xFFE5E7EB)

    val primaryDark = Color(0xFF60A5FA)
    val onPrimaryDark = Color(0xFF1E3A5F)
    val primaryContainerDark = Color(0xFF1E3A8A)
    val onPrimaryContainerDark = Color(0xFFDBEAFE)
    val secondaryDark = Color(0xFF818CF8)
    val onSecondaryDark = Color(0xFF1E1B4B)
    val secondaryContainerDark = Color(0xFF312E81)
    val onSecondaryContainerDark = Color(0xFFE0E7FF)
    val tertiaryDark = Color(0xFFF472B6)
    val onTertiaryDark = Color(0xFF500724)
    val tertiaryContainerDark = Color(0xFF831843)
    val onTertiaryContainerDark = Color(0xFFFCE7F3)
    val errorDark = Color(0xFFF87171)
    val onErrorDark = Color(0xFF450A0A)
    val errorContainerDark = Color(0xFF7F1D1D)
    val onErrorContainerDark = Color(0xFFFEE2E2)
    val backgroundDark = Color(0xFF0A0A0A)
    val onBackgroundDark = Color(0xFFFAFAFA)
    val surfaceDark = Color(0xFF0A0A0A)
    val onSurfaceDark = Color(0xFFFAFAFA)
    val surfaceVariantDark = Color(0xFF374151)
    val onSurfaceVariantDark = Color(0xFF9CA3AF)
    val outlineDark = Color(0xFF6B7280)
    val outlineVariantDark = Color(0xFF374151)
    val scrimDark = Color(0xFF000000)
    val inverseSurfaceDark = Color(0xFFF3F4F6)
    val inverseOnSurfaceDark = Color(0xFF1F2937)
    val inversePrimaryDark = Color(0xFF3B82F6)
    val surfaceDimDark = Color(0xFF0A0A0A)
    val surfaceBrightDark = Color(0xFF374151)
    val surfaceContainerLowestDark = Color(0xFF050505)
    val surfaceContainerLowDark = Color(0xFF111111)
    val surfaceContainerDark = Color(0xFF171717)
    val surfaceContainerHighDark = Color(0xFF1F1F1F)
    val surfaceContainerHighestDark = Color(0xFF2A2A2A)

    override val name: String = "CrossPaste"

    override val lightColorScheme =
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

    override val darkColorScheme =
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
}
