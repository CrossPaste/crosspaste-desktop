package com.clipevery.net.plugin

import com.clipevery.Dependencies
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.receiveChannel
import io.ktor.util.KtorDsl
import io.ktor.utils.io.core.readBytes
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.SignalProtocolStore

val SignalDecryption: ApplicationPlugin<SignalDecryptionConfig> = createApplicationPlugin(
    "SignalDecryption",
    ::SignalDecryptionConfig
) {

    val signalProtocolStore: SignalProtocolStore = pluginConfig.signalProtocolStore

    onCallReceive { call ->
        transformBody { data ->
            val headers = call.request.headers
            headers["appInstanceId"]?.let { appInstanceId ->
                headers["signal"]?.let { signal ->
                    if (signal == "1") {
                        val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
                        val encryptedContent = call.receiveChannel().readRemaining().readBytes()
                        val signalMessage = SignalMessage(encryptedContent)
                        val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)
                        return@transformBody sessionCipher.decrypt(signalMessage)
                    }
                }
            }
            return@transformBody data
        }
    }
}

@KtorDsl
class SignalDecryptionConfig {

    val signalProtocolStore: SignalProtocolStore = Dependencies.koinApplication.koin.get()
}
