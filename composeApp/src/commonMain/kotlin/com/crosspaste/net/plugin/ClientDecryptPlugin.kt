package com.crosspaste.net.plugin

import com.crosspaste.secure.SecureStore
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

class ClientDecryptPlugin(private val secureStore: SecureStore) :
    HttpClientPlugin<PluginConfig, ClientDecryptPlugin> {

    private val logger: KLogger = KotlinLogging.logger {}

    override val key = AttributeKey<ClientDecryptPlugin>("ClientDecryptPlugin")

    override fun prepare(block: PluginConfig.() -> Unit): ClientDecryptPlugin {
        return this
    }

    @OptIn(InternalAPI::class)
    override fun install(
        plugin: ClientDecryptPlugin,
        scope: HttpClient,
    ) {
        scope.receivePipeline.intercept(HttpReceivePipeline.Before) {
            val headers = it.call.request.headers
            headers["targetAppInstanceId"]?.let { appInstanceId ->
                headers["secure"]?.let { _ ->
                    logger.debug { "client decrypt $appInstanceId" }
                    val byteReadChannel: ByteReadChannel = it.content

                    val contentType = it.call.response.contentType()

                    val processor = secureStore.getMessageProcessor(appInstanceId)

                    if (contentType == ContentType.Application.Json) {
                        val bytes = byteReadChannel.readRemaining().readBytes()
                        val decrypt = processor.decrypt(bytes)

                        // Create a new ByteReadChannel to contain the decrypted content
                        val newChannel = ByteReadChannel(decrypt)
                        val responseData =
                            HttpResponseData(
                                it.status,
                                it.requestTime,
                                it.headers,
                                it.version,
                                newChannel,
                                it.coroutineContext,
                            )
                        proceedWith(DefaultHttpResponse(it.call, responseData))
                    } else if (contentType == ContentType.Application.OctetStream) {
                        val result =
                            buildPacket {
                                while (!byteReadChannel.isClosedForRead) {
                                    val size = byteReadChannel.readInt()
                                    val byteArray = ByteArray(size)
                                    var bytesRead = 0
                                    while (bytesRead < size) {
                                        val currentRead = byteReadChannel.readAvailable(byteArray, bytesRead, size - bytesRead)
                                        if (currentRead == -1) break
                                        bytesRead += currentRead
                                    }
                                    writeFully(processor.decrypt(byteArray))
                                }
                            }

                        val newChannel = ByteReadChannel(result.readBytes())
                        val responseData =
                            HttpResponseData(
                                it.status,
                                it.requestTime,
                                it.headers,
                                it.version,
                                newChannel,
                                it.coroutineContext,
                            )
                        proceedWith(DefaultHttpResponse(it.call, responseData))
                    }

                }
            }
        }
    }
}
