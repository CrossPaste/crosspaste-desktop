package com.clipevery.net.plugin

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.util.*
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SignalProtocolStore

@OptIn(InternalAPI::class)
val SIGNAL_SERVER_ENCRYPT_PLUGIN: ApplicationPlugin<SignalConfig> =
    createApplicationPlugin(
        "SignalServerEncryptPlugin",
        ::SignalConfig,
    ) {

        val logger: KLogger = KotlinLogging.logger {}

        val signalProtocolStore: SignalProtocolStore = pluginConfig.signalProtocolStore

        on(BeforeResponseTransform(ByteArray::class)) { call, body ->
            val headers = call.request.headers
            println("BeforeResponseTransform")
            headers["appInstanceId"]?.let { appInstanceId ->
                headers["signal"]?.let { signal ->
                    if (signal == "1") {
                        logger.debug { "signal server encrypt $appInstanceId" }
                        val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
                        val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)
                        val ciphertextMessage = sessionCipher.encrypt(body)
                        return@on ciphertextMessage.serialize()
                    }
                }
            }
            return@on body
        }
    }
