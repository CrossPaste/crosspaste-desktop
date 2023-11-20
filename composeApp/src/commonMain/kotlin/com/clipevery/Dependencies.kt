package com.clipevery

import androidx.compose.runtime.staticCompositionLocalOf
import com.clipevery.config.ConfigManager
import com.clipevery.encrypt.SignalProtocol
import com.clipevery.net.ClipServer
import com.clipevery.presist.FilePersist

abstract class Dependencies {
    abstract val clipServer: ClipServer
    abstract val configManager: ConfigManager
    abstract val filePersist: FilePersist
    abstract val signalProtocol: SignalProtocol
}

internal val LocalClipeveryServer = staticCompositionLocalOf<ClipServer> {
    noLocalProvidedFor("ClipeveryServer")
}

internal val LocalConfigManager = staticCompositionLocalOf<ConfigManager> {
    noLocalProvidedFor("ConfigManager")
}

internal val LocalFilePersist = staticCompositionLocalOf<FilePersist> {
    noLocalProvidedFor("FilePersist")
}

internal val LocalSignalProtocol = staticCompositionLocalOf<SignalProtocol> {
    noLocalProvidedFor("SignalProtocol")
}

private fun noLocalProvidedFor(name: String): Nothing {
    error("CompositionLocal $name not present")
}