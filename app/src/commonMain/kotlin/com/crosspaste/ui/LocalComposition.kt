package com.crosspaste.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
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

/**
 * Space at the end of a page's scrolling content. Mobile pages run edge-to-edge
 * under a transparent navigation bar and provide its height here, so the list
 * scrolls behind the bar and its last row still comes to rest above it.
 */
val LocalScrollContentBottomPadding =
    compositionLocalOf { 0.dp }
