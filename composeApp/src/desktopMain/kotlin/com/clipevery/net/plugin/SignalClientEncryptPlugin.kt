package com.clipevery.net.plugin

import com.clipevery.Clipevery
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SignalProtocolStore

object SignalClientEncryptPlugin : HttpClientPlugin<SignalConfig, SignalClientEncryptPlugin> {

    private val logger: KLogger = KotlinLogging.logger {}

    override val key = AttributeKey<SignalClientEncryptPlugin>("SignalClientEncryptPlugin")

    private val signalProtocolStore: SignalProtocolStore = Clipevery.koinApplication.koin.get()

    override fun prepare(block: SignalConfig.() -> Unit): SignalClientEncryptPlugin {
        return SignalClientEncryptPlugin
    }

    @OptIn(InternalAPI::class)
    override fun install(
        plugin: SignalClientEncryptPlugin,
        scope: HttpClient,
    ) {
        scope.sendPipeline.intercept(HttpSendPipeline.State) {
            context.headers["targetAppInstanceId"]?.let { targetAppInstanceId ->
                context.headers["signal"]?.let { signal ->
                    if (signal == "1") {
                        val originalContent = context.body as? OutgoingContent.ByteArrayContent
                        originalContent?.let {
                            logger.debug { "signal client encrypt $targetAppInstanceId" }
                            val signalProtocolAddress = SignalProtocolAddress(targetAppInstanceId, 1)
                            val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)
                            val ciphertextMessage = sessionCipher.encrypt(it.bytes())
                            val encryptedData = ciphertextMessage.serialize()
                            // Current all client requests use the Json protocol
                            context.body = ByteArrayContent(encryptedData, contentType = ContentType.Application.Json)
                            proceedWith(context.body)
                        }
                    }
                }
            }
        }
    }
}
