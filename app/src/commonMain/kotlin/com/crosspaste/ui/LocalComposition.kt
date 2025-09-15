package com.crosspaste.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

val LocalNavHostController =
    staticCompositionLocalOf<NavHostController> {
        error("CompositionLocal NavController not present")
    }
