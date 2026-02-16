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
import kotlinx.io.IOException
import kotlinx.io.readByteArray

class ClientDecryptPlugin(
    private val secureStore: SecureStore,
) : HttpClientPlugin<PluginConfig, ClientDecryptPlugin> {

    private val logger: KLogger = KotlinLogging.logger {}

    override val key = AttributeKey<ClientDecryptPlugin>("ClientDecryptPlugin")

    override fun prepare(block: PluginConfig.() -> Unit): ClientDecryptPlugin = this

    @OptIn(InternalAPI::class)
    override fun install(
        plugin: ClientDecryptPlugin,
        scope: HttpClient,
    ) {
        scope.receivePipeline.intercept(HttpReceivePipeline.Before) {
            val headers = it.call.request.headers
            headers["targetAppInstanceId"]?.let { appInstanceId ->
                headers["secure"]?.let { _ ->
                    if (!it.call.response.status
                            .isSuccess()
                    ) {
                        return@intercept
                    }
                    logger.debug { "client decrypt $appInstanceId" }
                    val byteReadChannel: ByteReadChannel = it.bodyAsChannel()

                    val contentType = it.call.response.contentType()

                    val processor = secureStore.getMessageProcessor(appInstanceId)

                    if (contentType?.match(ContentType.Application.Json) == true) {
                        // Note: reads entire JSON response into memory. Acceptable for LAN sync
                        // where JSON payloads (metadata, device info) are bounded and small.
                        val bytes = byteReadChannel.readRemaining().readByteArray()
                        val decrypt = processor.decrypt(bytes)

                        // Create a new ByteReadChannel to contain the decrypted content
                        val newChannel = ByteReadChannel(decrypt)
                        val responseData =
                            HttpResponseData(
                                it.status,
                                it.requestTime,
                                updateContentLength(it.headers, decrypt.size.toString()),
                                it.version,
                                newChannel,
                                it.coroutineContext,
                            )
                        proceedWith(DefaultHttpResponse(it.call, responseData))
                    } else if (contentType?.match(ContentType.Application.OctetStream) == true) {
                        val result =
                            buildPacket {
                                while (!byteReadChannel.isClosedForRead) {
                                    val size =
                                        try {
                                            byteReadChannel.readInt()
                                        } catch (_: IOException) {
                                            // Channel closed between isClosedForRead check and readInt().
                                            // All encrypted chunks were already read and decrypted.
                                            break
                                        }
                                    val byteArray = ByteArray(size)
                                    byteReadChannel.readFully(byteArray, 0, size)
                                    val decryptByteArray = processor.decrypt(byteArray)
                                    writeFully(decryptByteArray)
                                }
                            }

                        val byteArray = result.readByteArray()
                        val contentLength = byteArray.size.toString()
                        val newChannel = ByteReadChannel(byteArray)
                        val responseData =
                            HttpResponseData(
                                it.status,
                                it.requestTime,
                                updateContentLength(it.headers, contentLength),
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

    private fun updateContentLength(
        headers: Headers,
        newLength: String,
    ): Headers =
        Headers.build {
            appendAll(headers)
            remove(HttpHeaders.ContentLength)
            append(HttpHeaders.ContentLength, newLength)
        }
}
