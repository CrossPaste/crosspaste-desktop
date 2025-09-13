package com.crosspaste.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import com.crosspaste.app.ExitMode

internal val LocalExitApplication =
    staticCompositionLocalOf<(ExitMode) -> Unit> {
        error("CompositionLocal ExitApplication not present")
    }

internal val LocalNavHostController =
    staticCompositionLocalOf<NavHostController> {
        error("CompositionLocal NavController not present")
    }
