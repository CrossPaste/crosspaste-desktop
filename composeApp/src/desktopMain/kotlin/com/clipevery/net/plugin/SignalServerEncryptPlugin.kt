package com.clipevery.net.plugin

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SignalProtocolStore

val SIGNAL_SERVER_ENCRYPT_PLUGIN: ApplicationPlugin<SignalConfig> =
    createApplicationPlugin(
        "SignalServerEncryptPlugin",
        ::SignalConfig,
    ) {

        val logger: KLogger = KotlinLogging.logger {}

        val signalProtocolStore: SignalProtocolStore = pluginConfig.signalProtocolStore

        onCallRespond { call, message ->
            val headers = call.request.headers
            headers["appInstanceId"]?.let { appInstanceId ->
                headers["signal"]?.let {
                    logger.debug { "signal server encrypt $appInstanceId" }
                    call.response.pipeline.intercept(ApplicationSendPipeline.Before) { message ->
                        if (message is OutgoingContent.ByteArrayContent) {
                            val bytes = message.bytes()
                            val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
                            val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)
                            val ciphertextMessage = sessionCipher.encrypt(bytes)
                            val byteArrayContent = ByteArrayContent(ciphertextMessage.serialize(), contentType = message.contentType)
                            proceedWith(byteArrayContent)
                        } else {
                            proceed()
                        }
                    }
                }
            }
        }
    }
