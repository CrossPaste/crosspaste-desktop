package com.clipevery.net.plugin

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.*
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

        onCallRespond { call ->
            val headers = call.request.headers
            headers["appInstanceId"]?.let { appInstanceId ->
                headers["signal"]?.let {
                    logger.debug { "signal server encrypt $appInstanceId" }
                    call.response.pipeline.intercept(ApplicationSendPipeline.Transform) { message ->
                        val originalContent: Pair<ByteArray, ContentType?> =
                            when (message) {
                                is OutgoingContent.ByteArrayContent -> Pair(message.bytes(), message.contentType)
                                is OutgoingContent.ReadChannelContent -> Pair(message.readFrom().toByteArray(), message.contentType)
                                is OutgoingContent.WriteChannelContent -> {
                                    val channel = ByteChannel(true)
                                    message.writeTo(channel)
                                    Pair(channel.toByteArray(), message.contentType)
                                }

                                is OutgoingContent.NoContent -> Pair(ByteArray(0), null)
                                else -> Pair(message.toString().toByteArray(), null)
                            }

                        val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
                        val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)
                        val ciphertextMessage = sessionCipher.encrypt(originalContent.first)
                        val byteArrayContent =
                            ByteArrayContent(
                                ciphertextMessage.serialize(),
                                contentType = originalContent.second,
                            )

                        proceedWith(byteArrayContent)
                    }
                }
            }
        }
    }
