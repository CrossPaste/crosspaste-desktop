package com.clipevery.net.plugin

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress

val SIGNAL_SERVER_ENCRYPT_PLUGIN: ApplicationPlugin<SignalConfig> =
    createApplicationPlugin(
        "SignalServerEncryptPlugin",
        ::SignalConfig,
    ) {

        val logger: KLogger = KotlinLogging.logger {}

        val signalProtocolStore = pluginConfig.signalProtocolStore

        on(EncryptResponse) { call, body ->
            val headers = call.request.headers
            headers["appInstanceId"]?.let { appInstanceId ->
                headers["signal"]?.let {
                    logger.debug { "signal server encrypt $appInstanceId" }
                    transformBodyTo(body) { bytes ->
                        val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
                        val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)
                        val ciphertextMessage = sessionCipher.encrypt(bytes)
                        ciphertextMessage.serialize()
                    }
                }
            }
        }
    }

object EncryptResponse :
    Hook<suspend EncryptResponse.Context.(ApplicationCall, OutgoingContent) -> Unit> {
    class Context(private val context: PipelineContext<Any, ApplicationCall>) {
        suspend fun transformBodyTo(
            body: OutgoingContent,
            encrypt: (ByteArray) -> ByteArray,
        ) {
            when (body) {
                is OutgoingContent.ByteArrayContent -> {
                    val bytes = body.bytes()
                    context.subject = ByteArrayContent(encrypt(bytes), contentType = body.contentType, status = body.status)
                }

                is OutgoingContent.ReadChannelContent -> {
                    val bytes = body.readFrom().readRemaining().readBytes()
                    context.subject = ByteArrayContent(encrypt(bytes), contentType = body.contentType, status = body.status)
                }

                is OutgoingContent.WriteChannelContent -> {
                    val byteChannel = ByteChannel(true)
                    body.writeTo(byteChannel)
                    byteChannel.flush()
                    byteChannel.close()
                    val bytes = byteChannel.readRemaining().readBytes()
                    context.subject = ByteArrayContent(encrypt(bytes), contentType = body.contentType, status = body.status)
                }

                else -> {
                    context.subject = body
                }
            }
        }
    }

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend Context.(ApplicationCall, OutgoingContent) -> Unit,
    ) {
        pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) {
            handler(Context(this), call, subject as OutgoingContent)
        }
    }
}
