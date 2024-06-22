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
import java.io.ByteArrayOutputStream
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
            encrypt: suspend (ByteArray) -> ByteArray,
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
                        val originChannel = ByteChannel(true)
                        val encryptChannel = this
                        val targetBufferSize = 81920 // Target buffer size for each encryption operation

                        val deferred =
                            ioCoroutineDispatcher.async {
                                val largeBuffer = ByteArrayOutputStream(targetBufferSize)
                                val tempBuffer = ByteBuffer.allocateDirect(4096) // Temporary buffer for reading from the channel

                                while (true) {
                                    tempBuffer.clear()
                                    val readSize = originChannel.readAvailable(tempBuffer)
                                    if (readSize < 0 && largeBuffer.size() == 0) break // No more data to read and buffer is empty
                                    if (readSize > 0) {
                                        tempBuffer.flip()
                                        val bytes = ByteArray(tempBuffer.remaining())
                                        tempBuffer.get(bytes)
                                        largeBuffer.write(bytes, 0, bytes.size) // Accumulate bytes into the large buffer
                                    }

                                    // Check if largeBuffer is filled or no more data is available to read
                                    if (largeBuffer.size() >= targetBufferSize || (readSize < 0 && largeBuffer.size() > 0)) {
                                        val byteArray = largeBuffer.toByteArray()
                                        val encryptedData = encrypt(byteArray)
                                        encryptChannel.writeInt(encryptedData.size)
                                        encryptChannel.writeFully(encryptedData, 0, encryptedData.size)
                                        largeBuffer.reset() // Reset the buffer after processing
                                    }
                                }
                            }

                        body.writeTo(originChannel)
                        originChannel.close() // Close the original channel after all data is written
                        deferred.await() // Wait for all encryption and writing to complete
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
