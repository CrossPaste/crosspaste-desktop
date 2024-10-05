package com.crosspaste.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf
import com.crosspaste.app.ExitMode

internal val LocalExitApplication =
    staticCompositionLocalOf<(ExitMode) -> Unit> {
        noLocalProvidedFor("ExitApplication")
    }

val LocalScreenContent =
    staticCompositionLocalOf<MutableState<ScreenContext>> {
        noLocalProvidedFor("ScreenContext")
    }

private fun noLocalProvidedFor(name: String): Nothing {
    error("CompositionLocal $name not present")
}
