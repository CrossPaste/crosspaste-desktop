package com.clipevery

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf
import com.clipevery.ui.PageViewContext
import org.koin.core.KoinApplication

internal val LocalKoinApplication =
    staticCompositionLocalOf<KoinApplication> {
        noLocalProvidedFor("KoinApplication")
    }

internal val LocalExitApplication =
    staticCompositionLocalOf<() -> Unit> {
        noLocalProvidedFor("ExitApplication")
    }

internal val LocalPageViewContent =
    staticCompositionLocalOf<MutableState<PageViewContext>> {
        noLocalProvidedFor("PageViewContext")
    }

private fun noLocalProvidedFor(name: String): Nothing {
    error("CompositionLocal $name not present")
}
