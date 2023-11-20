package com.clipevery

import androidx.compose.runtime.staticCompositionLocalOf
import com.clipevery.net.ClipServer
import com.clipevery.net.ConfigManager

abstract class Dependencies {
    abstract val clipServer: ClipServer
    abstract val configManager: ConfigManager
}

internal val LocalClipeveryServer = staticCompositionLocalOf<ClipServer> {
    noLocalProvidedFor("ClipeveryServer")
}

internal val LocalConfigManager = staticCompositionLocalOf<ConfigManager> {
    noLocalProvidedFor("ConfigManager")
}

private fun noLocalProvidedFor(name: String): Nothing {
    error("CompositionLocal $name not present")
}