package com.clipevery.net.plugin

import com.clipevery.signal.SignalProcessorCache
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*

class SignalClientEncryptPlugin(private val signalProcessorCache: SignalProcessorCache) :
    HttpClientPlugin<SignalConfig, SignalClientEncryptPlugin> {

    private val logger: KLogger = KotlinLogging.logger {}

    override val key = AttributeKey<SignalClientEncryptPlugin>("SignalClientEncryptPlugin")

    override fun prepare(block: SignalConfig.() -> Unit): SignalClientEncryptPlugin {
        return this
    }

    override fun install(
        plugin: SignalClientEncryptPlugin,
        scope: HttpClient,
    ) {
        scope.sendPipeline.intercept(HttpSendPipeline.State) {
            context.headers["targetAppInstanceId"]?.let { targetAppInstanceId ->
                context.headers["signal"]?.let { signal ->
                    if (signal == "1") {
                        logger.debug { "signal client encrypt $targetAppInstanceId" }
                        when (context.body) {
                            // Current all client requests use the Json protocol
                            is OutgoingContent.ByteArrayContent -> {
                                val processor = signalProcessorCache.getSignalMessageProcessor(targetAppInstanceId)
                                val originalContent = context.body as OutgoingContent.ByteArrayContent
                                val ciphertextMessage = processor.encrypt(originalContent.bytes())
                                val encryptedData = ciphertextMessage.serialize()
                                proceedWith(ByteArrayContent(encryptedData, contentType = ContentType.Application.Json))
                            }
                        }
                    }
                }
            }
        }
    }
}
