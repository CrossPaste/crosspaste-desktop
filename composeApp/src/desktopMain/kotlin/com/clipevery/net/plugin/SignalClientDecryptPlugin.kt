package com.clipevery.net.plugin

import com.clipevery.Clipevery
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
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.SignalProtocolStore
import java.io.ByteArrayOutputStream

object SignalClientDecryptPlugin : HttpClientPlugin<SignalConfig, SignalClientDecryptPlugin> {

    private val logger: KLogger = KotlinLogging.logger {}

    override val key = AttributeKey<SignalClientDecryptPlugin>("SignalClientDecryptPlugin")

    private val signalProtocolStore: SignalProtocolStore = Clipevery.koinApplication.koin.get()

    override fun prepare(block: SignalConfig.() -> Unit): SignalClientDecryptPlugin {
        return SignalClientDecryptPlugin
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

                        if (contentType == ContentType.Application.Json) {
                            val bytes = byteReadChannel.readRemaining().readBytes()
                            val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
                            val signalMessage = SignalMessage(bytes)
                            val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)
                            val decrypt = sessionCipher.decrypt(signalMessage)

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
                            val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
                            val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)

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
                                val signalMessage = SignalMessage(byteArray)
                                result.write(sessionCipher.decrypt(signalMessage))
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
