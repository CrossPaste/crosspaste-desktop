package com.crosspaste.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.crosspaste.app.DesktopAppSizeValue
import com.crosspaste.app.ExitMode
import com.crosspaste.app.WindowInfo

internal val LocalExitApplication =
    staticCompositionLocalOf<(ExitMode) -> Unit> {
        error("CompositionLocal ExitApplication not present")
    }

internal val LocalDesktopAppSizeValueState =
    staticCompositionLocalOf<DesktopAppSizeValue> {
        error("CompositionLocal DesktopAppSizeValue not present")
    }

internal val LocalMainWindowInfoState =
    staticCompositionLocalOf<WindowInfo> {
        error("CompositionLocal MainWindowInfo not present")
    }

internal val LocalSearchWindowInfoState =
    staticCompositionLocalOf<WindowInfo> {
        error("CompositionLocal SearchWindowInfo not present")
    }
