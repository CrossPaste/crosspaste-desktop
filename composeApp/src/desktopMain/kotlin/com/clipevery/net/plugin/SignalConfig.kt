package com.clipevery.net.plugin

import com.clipevery.Clipevery
import io.ktor.util.*
import org.signal.libsignal.protocol.state.SignalProtocolStore

@KtorDsl
class SignalConfig {

    val signalProtocolStore: SignalProtocolStore = Clipevery.koinApplication.koin.get()
}
