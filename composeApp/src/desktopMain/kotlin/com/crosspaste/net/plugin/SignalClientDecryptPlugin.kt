package com.crosspaste.net.plugin

import com.crosspaste.signal.SignalProcessorCache
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
import java.io.ByteArrayOutputStream

class SignalClientDecryptPlugin(private val signalProcessorCache: SignalProcessorCache) :
    HttpClientPlugin<SignalConfig, SignalClientDecryptPlugin> {

    private val logger: KLogger = KotlinLogging.logger {}

    override val key = AttributeKey<SignalClientDecryptPlugin>("SignalClientDecryptPlugin")

    override fun prepare(block: SignalConfig.() -> Unit): SignalClientDecryptPlugin {
        return this
    }

    @OptIn(InternalAPI::class)
    override fun install(
        plugin: SignalClientDecryptPlugin,
        scope: HttpClient,
    ) {
        scope.receivePipeline.intercept(HttpReceivePipeline.Before) {
            val headers = it.call.request.headers
            headers["targetAppInstanceId"]?.let { appInstanceId ->
                headers["signal"]?.let { signal ->
                    if (signal == "1") {
                        logger.debug { "signal client decrypt $appInstanceId" }
                        val byteReadChannel: ByteReadChannel = it.content

                        val contentType = it.call.response.contentType()

                        val processor = signalProcessorCache.getSignalMessageProcessor(appInstanceId)

                        if (contentType == ContentType.Application.Json) {
                            val bytes = byteReadChannel.readRemaining().readBytes()
                            val decrypt = processor.decryptSignalMessage(bytes)

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
                            val result = ByteArrayOutputStream()
                            while (!byteReadChannel.isClosedForRead) {
                                val size = byteReadChannel.readInt()
                                val byteArray = ByteArray(size)
                                var bytesRead = 0
                                while (bytesRead < size) {
                                    val currentRead = byteReadChannel.readAvailable(byteArray, bytesRead, size - bytesRead)
                                    if (currentRead == -1) break
                                    bytesRead += currentRead
                                }
                                result.write(processor.decryptSignalMessage(byteArray))
                            }
                            val newChannel = ByteReadChannel(result.toByteArray())
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
}
