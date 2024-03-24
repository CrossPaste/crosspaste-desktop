package com.clipevery.net.plugin

import com.clipevery.Dependencies
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.content.ByteArrayContent
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.SignalProtocolStore
import java.nio.ByteBuffer

val SignalServerDecryption: ApplicationPlugin<SignalConfig> = createApplicationPlugin(
    "SignalDecryption",
    ::SignalConfig
) {

    val signalProtocolStore: SignalProtocolStore = pluginConfig.signalProtocolStore

    on(ReceiveRequestBytes) { call, body ->
        val headers = call.request.headers
        headers["appInstanceId"]?.let { appInstanceId ->
            headers["signal"]?.let { signal ->
                if (signal == "1") {
                    call.request.path()
                    return@on application.writer {
                        val encryptedContent = body.readRemaining().readBytes()
                        val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
                        val signalMessage = SignalMessage(encryptedContent)
                        val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)
                        val decrypt = sessionCipher.decrypt(signalMessage)
                        channel.writeFully(ByteBuffer.wrap(decrypt))
                    }.channel
                }
            }
        }
        return@on body
    }
}

@KtorDsl
class SignalConfig {

    val signalProtocolStore: SignalProtocolStore = Dependencies.koinApplication.koin.get()
}

object SignalClientEncryption : HttpClientPlugin<SignalConfig, SignalClientEncryption> {
    override val key = AttributeKey<SignalClientEncryption>("SignalClientEncryption")

    val signalProtocolStore: SignalProtocolStore = Dependencies.koinApplication.koin.get()

    override fun prepare(block: SignalConfig.() -> Unit): SignalClientEncryption {
        return SignalClientEncryption
    }

    @OptIn(InternalAPI::class)
    override fun install(plugin: SignalClientEncryption, scope: HttpClient) {

        scope.sendPipeline.intercept(HttpSendPipeline.State) {
            context.headers["appInstanceId"]?.let { appInstanceId ->
                context.headers["signal"]?.let { signal ->
                    if (signal == "1") {
                        val originalContent = context.body as? OutgoingContent.ByteArrayContent
                        originalContent?.let {
                            context.headers["targetAppInstanceId"]?.let { targetAppInstanceId ->
                                context.headers.remove("targetAppInstanceId")
                                val signalProtocolAddress = SignalProtocolAddress(targetAppInstanceId, 1)
                                val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)
                                val ciphertextMessage = sessionCipher.encrypt(it.bytes())
                                val encryptedData = ciphertextMessage.serialize()
                                context.body = ByteArrayContent(encryptedData)
                                proceedWith(context.body)
                            } ?: run {
                                throw IllegalStateException("targetAppInstanceId is null")
                            }
                        }
                    }
                }
            }
        }
    }
}
