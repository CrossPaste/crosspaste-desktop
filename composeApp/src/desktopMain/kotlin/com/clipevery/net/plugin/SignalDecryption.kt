package com.clipevery.net.plugin

import com.clipevery.Dependencies
import com.clipevery.utils.base64mimeDecode
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ReceiveRequestBytes
import io.ktor.util.KtorDsl
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.writer
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.SignalProtocolStore
import java.nio.ByteBuffer

val SignalDecryption: ApplicationPlugin<SignalDecryptionConfig> = createApplicationPlugin(
    "SignalDecryption",
    ::SignalDecryptionConfig
) {

    val signalProtocolStore: SignalProtocolStore = pluginConfig.signalProtocolStore

    on(ReceiveRequestBytes) { call, body ->
        val headers = call.request.headers
        headers["appInstanceId"]?.let { appInstanceId ->
            headers["signal"]?.let { signal ->
                if (signal == "1") {
                    return@on application.writer {
                        val base64Content = body.readRemaining().readBytes()
                        val originalString = String(base64Content, Charsets.UTF_8)
                        val base64String = originalString.substring(1, originalString.length - 1)
                        val encryptedContent = base64mimeDecode(base64String)
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
class SignalDecryptionConfig {

    val signalProtocolStore: SignalProtocolStore = Dependencies.koinApplication.koin.get()
}
