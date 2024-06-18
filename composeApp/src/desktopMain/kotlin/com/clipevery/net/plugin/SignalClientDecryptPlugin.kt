package com.clipevery.net.plugin

import com.clipevery.Clipevery
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.SignalProtocolStore

object SignalClientDecryptPlugin : HttpClientPlugin<SignalConfig, SignalClientDecryptPlugin> {

    override val key = AttributeKey<SignalClientDecryptPlugin>("SignalClientDecryptPlugin")

    private val signalProtocolStore: SignalProtocolStore = Clipevery.koinApplication.koin.get()

    override fun prepare(block: SignalConfig.() -> Unit): SignalClientDecryptPlugin {
        return SignalClientDecryptPlugin
    }

    @OptIn(InternalAPI::class)
    override fun install(
        plugin: SignalClientDecryptPlugin,
        scope: HttpClient,
    ) {
        scope.receivePipeline.intercept(HttpReceivePipeline.State) {
            val byteReadChannel: ByteReadChannel = it.content
            val bytes = byteReadChannel.readRemaining().readBytes()
            val headers = it.call.request.headers
            headers["targetAppInstanceId"]?.let { appInstanceId ->
                headers["signal"]?.let { signal ->
                    if (signal == "1") {
                        val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
                        val signalMessage = SignalMessage(bytes)
                        val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)
                        val decrypt = sessionCipher.decrypt(signalMessage)
                        it.content.readFully(decrypt)
                    }
                }
            }
        }
    }
}
