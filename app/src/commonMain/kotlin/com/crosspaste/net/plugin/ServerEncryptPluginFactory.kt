package com.crosspaste.net.plugin

import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.io.readByteArray

class ServerEncryptPluginFactory(
    private val secureStore: SecureStore,
) {

    fun createPlugin(): ApplicationPlugin<PluginConfig> {
        return createApplicationPlugin(
            "SecureServerEncryptPlugin",
            { PluginConfig(secureStore) },
        ) {
            val logger: KLogger = KotlinLogging.logger {}

            val secureStore: SecureStore = pluginConfig.secureStore

            on(EncryptResponse) { call, body ->
                val headers = call.request.headers
                headers["appInstanceId"]?.let { appInstanceId ->
                    headers["secure"]?.let {
                        if (call.response.status()?.isSuccess() == false) {
                            return@let
                        }
                        logger.debug { "server encrypt $appInstanceId" }
                        val processor = secureStore.getMessageProcessor(appInstanceId)
                        transformBodyTo(body) { bytes ->
                            processor.encrypt(bytes)
                        }
                    }
                }
            }
        }
    }
}

object EncryptResponse :
    Hook<suspend EncryptResponse.Context.(ApplicationCall, OutgoingContent) -> Unit> {

    private const val ENCRYPT_CHUNK_SIZE = 1024 * 256

    class Context(
        private val context: PipelineContext<Any, PipelineCall>,
    ) {
        suspend fun transformBodyTo(
            body: OutgoingContent,
            encrypt: suspend (ByteArray) -> ByteArray,
        ) {
            when (body) {
                is OutgoingContent.ByteArrayContent -> {
                    val bytes = body.bytes()
                    context.subject =
                        ByteArrayContent(
                            encrypt(bytes),
                            contentType = body.contentType,
                            status = body.status,
                        )
                }

                is OutgoingContent.ReadChannelContent -> {
                    // Note: reads entire body into memory for encryption. Acceptable for small
                    // responses (JSON metadata). Large payloads use WriteChannelContent with chunked streaming.
                    val bytes = body.readFrom().readRemaining().readByteArray()
                    context.subject =
                        ByteArrayContent(
                            encrypt(bytes),
                            contentType = body.contentType,
                            status = body.status,
                        )
                }

                is OutgoingContent.WriteChannelContent -> {
                    val producer: suspend ByteWriteChannel.() -> Unit = {
                        val originChannel = ByteChannel(true)
                        val encryptChannel = this

                        coroutineScope {
                            val deferred =
                                async(ioDispatcher) {
                                    val buffer = ByteArray(ENCRYPT_CHUNK_SIZE)
                                    while (true) {
                                        val bytesRead = originChannel.readAvailable(buffer)
                                        if (bytesRead <= 0) break
                                        val chunk =
                                            if (bytesRead == ENCRYPT_CHUNK_SIZE) {
                                                buffer
                                            } else {
                                                buffer.copyOf(bytesRead)
                                            }
                                        val encryptedData = encrypt(chunk)
                                        encryptChannel.writeInt(encryptedData.size)
                                        encryptChannel.writeFully(encryptedData)
                                    }
                                }

                            runCatching {
                                body.writeTo(originChannel)
                            }.onFailure { e ->
                                originChannel.close(e)
                            }.onSuccess {
                                originChannel.close()
                            }
                            deferred.await()
                        }
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
