package com.crosspaste

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf
import com.crosspaste.app.ExitMode
import com.crosspaste.ui.PageViewContext

internal val LocalExitApplication =
    staticCompositionLocalOf<(ExitMode) -> Unit> {
        noLocalProvidedFor("ExitApplication")
    }

internal val LocalPageViewContent =
    staticCompositionLocalOf<MutableState<PageViewContext>> {
        noLocalProvidedFor("PageViewContext")
    }

private fun noLocalProvidedFor(name: String): Nothing {
    error("CompositionLocal $name not present")
}
