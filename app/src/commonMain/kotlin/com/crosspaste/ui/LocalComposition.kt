package com.crosspaste.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import com.crosspaste.app.AppSizeValue
import com.crosspaste.ui.theme.ThemeExt
import com.crosspaste.ui.theme.ThemeState

val LocalNavHostController =
    staticCompositionLocalOf<NavHostController> {
        error("CompositionLocal NavController not present")
    }

val LocalThemeState =
    staticCompositionLocalOf<ThemeState> {
        error("CompositionLocal Local themeState not present")
    }

val LocalThemeExtState =
    staticCompositionLocalOf<ThemeExt> {
        error("CompositionLocal ThemeExt not present")
    }

val LocalAppSizeValueState =
    staticCompositionLocalOf<AppSizeValue> {
        error("CompositionLocal AppSizeValue not present")
    }

val LocalSmallSettingItemState =
    staticCompositionLocalOf { false }
