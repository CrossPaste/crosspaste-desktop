package com.clipevery.net.plugin

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.SignalProtocolStore
import java.nio.ByteBuffer

val SIGNAL_SERVER_DECRYPT_PLUGIN: ApplicationPlugin<SignalConfig> =
    createApplicationPlugin(
        "SignalServerDecryptPlugin",
        ::SignalConfig,
    ) {

        val signalProtocolStore: SignalProtocolStore = pluginConfig.signalProtocolStore

        on(ReceiveRequestBytes) { call, body ->
            val headers = call.request.headers
            headers["appInstanceId"]?.let { appInstanceId ->
                headers["signal"]?.let { signal ->
                    if (signal == "1") {
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
