package com.clipevery

import androidx.compose.runtime.staticCompositionLocalOf
import org.koin.core.KoinApplication

internal val LocalKoinApplication = staticCompositionLocalOf<KoinApplication> {
    noLocalProvidedFor("KoinApplication")
}

private fun noLocalProvidedFor(name: String): Nothing {
    error("CompositionLocal $name not present")
}