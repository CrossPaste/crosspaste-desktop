package com.crosspaste.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object SeaColor : ThemeColor {

    val primaryLight = Color(0xFF415F91)
    val onPrimaryLight = Color(0xFFFFFFFF)
    val primaryContainerLight = Color(0xFFD6E3FF)
    val onPrimaryContainerLight = Color(0xFF001B3E)
    val secondaryLight = Color(0xFF565F71)
    val onSecondaryLight = Color(0xFFFFFFFF)
    val secondaryContainerLight = Color(0xFFDAE2F9)
    val onSecondaryContainerLight = Color(0xFF131C2B)
    val tertiaryLight = Color(0xFF705575)
    val onTertiaryLight = Color(0xFFFFFFFF)
    val tertiaryContainerLight = Color(0xFFFAD8FD)
    val onTertiaryContainerLight = Color(0xFF28132E)
    val errorLight = Color(0xFFBA1A1A)
    val onErrorLight = Color(0xFFFFFFFF)
    val errorContainerLight = Color(0xFFFFDAD6)
    val onErrorContainerLight = Color(0xFF410002)
    val backgroundLight = Color(0xFFF9F9FF)
    val onBackgroundLight = Color(0xFF191C20)
    val surfaceLight = Color(0xFFF9F9FF)
    val onSurfaceLight = Color(0xFF191C20)
    val surfaceVariantLight = Color(0xFFE0E2EC)
    val onSurfaceVariantLight = Color(0xFF44474E)
    val outlineLight = Color(0xFF74777F)
    val outlineVariantLight = Color(0xFFC4C6D0)
    val scrimLight = Color(0xFF000000)
    val inverseSurfaceLight = Color(0xFF2E3036)
    val inverseOnSurfaceLight = Color(0xFFF0F0F7)
    val inversePrimaryLight = Color(0xFFAAC7FF)
    val surfaceDimLight = Color(0xFFD9D9E0)
    val surfaceBrightLight = Color(0xFFF9F9FF)
    val surfaceContainerLowestLight = Color(0xFFFFFFFF)
    val surfaceContainerLowLight = Color(0xFFF3F3FA)
    val surfaceContainerLight = Color(0xFFEDEDF4)
    val surfaceContainerHighLight = Color(0xFFE7E8EE)
    val surfaceContainerHighestLight = Color(0xFFE2E2E9)

    val primaryDark = Color(0xFFAAC7FF)
    val onPrimaryDark = Color(0xFF0A305F)
    val primaryContainerDark = Color(0xFF284777)
    val onPrimaryContainerDark = Color(0xFFD6E3FF)
    val secondaryDark = Color(0xFFBEC6DC)
    val onSecondaryDark = Color(0xFF283141)
    val secondaryContainerDark = Color(0xFF3E4759)
    val onSecondaryContainerDark = Color(0xFFDAE2F9)
    val tertiaryDark = Color(0xFFDDBCE0)
    val onTertiaryDark = Color(0xFF3F2844)
    val tertiaryContainerDark = Color(0xFF573E5C)
    val onTertiaryContainerDark = Color(0xFFFAD8FD)
    val errorDark = Color(0xFFFFB4AB)
    val onErrorDark = Color(0xFF690005)
    val errorContainerDark = Color(0xFF93000A)
    val onErrorContainerDark = Color(0xFFFFDAD6)
    val backgroundDark = Color(0xFF111318)
    val onBackgroundDark = Color(0xFFE2E2E9)
    val surfaceDark = Color(0xFF111318)
    val onSurfaceDark = Color(0xFFE2E2E9)
    val surfaceVariantDark = Color(0xFF44474E)
    val onSurfaceVariantDark = Color(0xFFC4C6D0)
    val outlineDark = Color(0xFF8E9099)
    val outlineVariantDark = Color(0xFF44474E)
    val scrimDark = Color(0xFF000000)
    val inverseSurfaceDark = Color(0xFFE2E2E9)
    val inverseOnSurfaceDark = Color(0xFF2E3036)
    val inversePrimaryDark = Color(0xFF415F91)
    val surfaceDimDark = Color(0xFF111318)
    val surfaceBrightDark = Color(0xFF37393E)
    val surfaceContainerLowestDark = Color(0xFF0C0E13)
    val surfaceContainerLowDark = Color(0xFF191C20)
    val surfaceContainerDark = Color(0xFF1D2024)
    val surfaceContainerHighDark = Color(0xFF282A2F)
    val surfaceContainerHighestDark = Color(0xFF33353A)

    override val name: String = "Sea"

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
