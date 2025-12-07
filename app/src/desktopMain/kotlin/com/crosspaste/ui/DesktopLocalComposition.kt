package com.crosspaste.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.crosspaste.app.DesktopAppSizeValue
import com.crosspaste.app.ExitMode

internal val LocalExitApplication =
    staticCompositionLocalOf<(ExitMode) -> Unit> {
        error("CompositionLocal ExitApplication not present")
    }

internal val LocalDesktopAppSizeValueState =
    staticCompositionLocalOf<DesktopAppSizeValue> {
        error("CompositionLocal DesktopAppSizeValue not present")
    }
