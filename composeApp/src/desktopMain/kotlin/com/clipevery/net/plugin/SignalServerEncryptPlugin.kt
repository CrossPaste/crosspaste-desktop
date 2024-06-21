package com.clipevery.net.plugin

import com.clipevery.signal.SignalProcessorCache
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.nio.ByteBuffer

val SIGNAL_SERVER_ENCRYPT_PLUGIN: ApplicationPlugin<SignalConfig> =
    createApplicationPlugin(
        "SignalServerEncryptPlugin",
        ::SignalConfig,
    ) {

        val logger: KLogger = KotlinLogging.logger {}

        val signalProcessorCache: SignalProcessorCache = pluginConfig.signalProcessorCache

        on(EncryptResponse) { call, body ->
            val headers = call.request.headers
            headers["appInstanceId"]?.let { appInstanceId ->
                headers["signal"]?.let {
                    logger.debug { "signal server encrypt $appInstanceId" }
                    val processor = signalProcessorCache.getSignalMessageProcessor(appInstanceId)
                    transformBodyTo(body) { bytes ->
                        processor.encrypt(bytes).serialize()
                    }
                }
            }
        }
    }

object EncryptResponse :
    Hook<suspend EncryptResponse.Context.(ApplicationCall, OutgoingContent) -> Unit> {

    val ioCoroutineDispatcher = CoroutineScope(Dispatchers.IO)

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
                    val producer: suspend ByteWriteChannel.() -> Unit = {
                        val encryptChannel: ByteWriteChannel = this
                        val originChannel = ByteChannel(true)
                        val byteBuffer = ByteBuffer.allocateDirect(81920)

                        val deferred =
                            ioCoroutineDispatcher.async {
                                while (true) {
                                    byteBuffer.clear()
                                    val size = originChannel.readAvailable(byteBuffer)
                                    if (size < 0) break
                                    if (size == 0) continue
                                    byteBuffer.flip()
                                    val byteArray = ByteArray(byteBuffer.remaining())
                                    byteBuffer.get(byteArray)
                                    val transformedBytes = encrypt(byteArray)
                                    encryptChannel.writeInt(transformedBytes.size)
                                    var offset = 0
                                    do {
                                        val availableSize =
                                            encryptChannel.writeAvailable(
                                                transformedBytes,
                                                offset,
                                                transformedBytes.size - offset,
                                            )
                                        offset += availableSize
                                    } while (transformedBytes.size > offset)
                                }
                            }

                        body.writeTo(originChannel)
                        originChannel.close()

                        deferred.await()
                    }

                    val content =
                        ChannelWriterContent(
                            body = producer,
                            contentType = body.contentType,
                            status = body.status,
                        )
                    context.subject = content
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
