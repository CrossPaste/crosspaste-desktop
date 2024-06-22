package com.clipevery.net.plugin

import com.clipevery.Clipevery
import com.clipevery.signal.SignalProcessorCache
import io.ktor.util.*

@KtorDsl
class SignalConfig {

    val signalProcessorCache: SignalProcessorCache = Clipevery.koinApplication.koin.get()
}
