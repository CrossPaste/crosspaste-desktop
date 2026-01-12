package com.crosspaste.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object GrassColor : ThemeColor {
    val primaryLight = Color(0xFF4C662B)
    val onPrimaryLight = Color(0xFFFFFFFF)
    val primaryContainerLight = Color(0xFFCDEDA3)
    val onPrimaryContainerLight = Color(0xFF102000)
    val secondaryLight = Color(0xFF586249)
    val onSecondaryLight = Color(0xFFFFFFFF)
    val secondaryContainerLight = Color(0xFFDCE7C8)
    val onSecondaryContainerLight = Color(0xFF151E0B)
    val tertiaryLight = Color(0xFF386663)
    val onTertiaryLight = Color(0xFFFFFFFF)
    val tertiaryContainerLight = Color(0xFFBCECE7)
    val onTertiaryContainerLight = Color(0xFF00201E)
    val errorLight = Color(0xFFBA1A1A)
    val onErrorLight = Color(0xFFFFFFFF)
    val errorContainerLight = Color(0xFFFFDAD6)
    val onErrorContainerLight = Color(0xFF410002)
    val backgroundLight = Color(0xFFF9FAEF)
    val onBackgroundLight = Color(0xFF1A1C16)
    val surfaceLight = Color(0xFFF9FAEF)
    val onSurfaceLight = Color(0xFF1A1C16)
    val surfaceVariantLight = Color(0xFFE1E4D5)
    val onSurfaceVariantLight = Color(0xFF44483D)
    val outlineLight = Color(0xFF75796C)
    val outlineVariantLight = Color(0xFFC5C8BA)
    val scrimLight = Color(0xFF000000)
    val inverseSurfaceLight = Color(0xFF2F312A)
    val inverseOnSurfaceLight = Color(0xFFF1F2E6)
    val inversePrimaryLight = Color(0xFFB1D18A)
    val surfaceDimLight = Color(0xFFDADBD0)
    val surfaceBrightLight = Color(0xFFF9FAEF)
    val surfaceContainerLowestLight = Color(0xFFFFFFFF)
    val surfaceContainerLowLight = Color(0xFFF3F4E9)
    val surfaceContainerLight = Color(0xFFEEEFE3)
    val surfaceContainerHighLight = Color(0xFFE8E9DE)
    val surfaceContainerHighestLight = Color(0xFFE2E3D8)

    val primaryDark = Color(0xFFB1D18A)
    val onPrimaryDark = Color(0xFF1F3701)
    val primaryContainerDark = Color(0xFF354E16)
    val onPrimaryContainerDark = Color(0xFFCDEDA3)
    val secondaryDark = Color(0xFFBFCBAD)
    val onSecondaryDark = Color(0xFF2A331E)
    val secondaryContainerDark = Color(0xFF404A33)
    val onSecondaryContainerDark = Color(0xFFDCE7C8)
    val tertiaryDark = Color(0xFFA0D0CB)
    val onTertiaryDark = Color(0xFF003735)
    val tertiaryContainerDark = Color(0xFF1F4E4B)
    val onTertiaryContainerDark = Color(0xFFBCECE7)
    val errorDark = Color(0xFFFFB4AB)
    val onErrorDark = Color(0xFF690005)
    val errorContainerDark = Color(0xFF93000A)
    val onErrorContainerDark = Color(0xFFFFDAD6)
    val backgroundDark = Color(0xFF12140E)
    val onBackgroundDark = Color(0xFFE2E3D8)
    val surfaceDark = Color(0xFF12140E)
    val onSurfaceDark = Color(0xFFE2E3D8)
    val surfaceVariantDark = Color(0xFF44483D)
    val onSurfaceVariantDark = Color(0xFFC5C8BA)
    val outlineDark = Color(0xFF8F9285)
    val outlineVariantDark = Color(0xFF44483D)
    val scrimDark = Color(0xFF000000)
    val inverseSurfaceDark = Color(0xFFE2E3D8)
    val inverseOnSurfaceDark = Color(0xFF2F312A)
    val inversePrimaryDark = Color(0xFF4C662B)
    val surfaceDimDark = Color(0xFF12140E)
    val surfaceBrightDark = Color(0xFF383A32)
    val surfaceContainerLowestDark = Color(0xFF0C0F09)
    val surfaceContainerLowDark = Color(0xFF1A1C16)
    val surfaceContainerDark = Color(0xFF1E201A)
    val surfaceContainerHighDark = Color(0xFF282B24)
    val surfaceContainerHighestDark = Color(0xFF33362E)

    override val name: String = "Grass"

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
