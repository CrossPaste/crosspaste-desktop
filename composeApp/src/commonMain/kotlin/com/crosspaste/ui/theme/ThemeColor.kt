package com.crosspaste.ui.theme

import androidx.compose.material3.ColorScheme

interface ThemeColor {

    val name: String

    val lightColorScheme: ColorScheme

    val lightMediumContrastColorScheme: ColorScheme

    val lightHighContrastColorScheme: ColorScheme

    val darkColorScheme: ColorScheme

    val darkMediumContrastColorScheme: ColorScheme

    val darkHighContrastColorScheme: ColorScheme
}
