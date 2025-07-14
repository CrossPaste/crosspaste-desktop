package com.crosspaste.net.plugin

import com.crosspaste.secure.SecureStore
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*

class ClientEncryptPlugin(
    private val secureStore: SecureStore,
) : HttpClientPlugin<PluginConfig, ClientEncryptPlugin> {

    private val logger: KLogger = KotlinLogging.logger {}

    override val key = AttributeKey<ClientEncryptPlugin>("ClientEncryptPlugin")

    override fun prepare(block: PluginConfig.() -> Unit): ClientEncryptPlugin = this

    @OptIn(InternalAPI::class)
    override fun install(
        plugin: ClientEncryptPlugin,
        scope: HttpClient,
    ) {
        scope.sendPipeline.intercept(HttpSendPipeline.Before) {
            context.headers["targetAppInstanceId"]?.let { targetAppInstanceId ->
                context.headers["secure"]?.let {
                    logger.debug { "client encrypt $targetAppInstanceId" }
                    when (context.body) {
                        // Current all client requests use the Json protocol
                        is OutgoingContent.ByteArrayContent -> {
                            val processor = secureStore.getMessageProcessor(targetAppInstanceId)
                            val originalContent = context.body as OutgoingContent.ByteArrayContent
                            val bytes = originalContent.bytes()
                            val ciphertextMessageBytes = processor.encrypt(bytes)
                            context.body =
                                ByteArrayContent(ciphertextMessageBytes, contentType = ContentType.Application.Json)
                        }
                    }
                }
            }
        }
    }
}
