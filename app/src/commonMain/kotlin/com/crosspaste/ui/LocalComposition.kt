package com.crosspaste.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import com.crosspaste.ui.theme.ThemeState

val LocalNavHostController =
    staticCompositionLocalOf<NavHostController> {
        error("CompositionLocal NavController not present")
    }

val LocalThemeState =
    staticCompositionLocalOf<ThemeState> {
        error("CompositionLocal Local themeState not present")
    }
