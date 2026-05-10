package com.crosspaste.net.plugin

import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
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
                    when (val body = context.body) {
                        is OutgoingContent.ByteArrayContent -> {
                            val processor = secureStore.getMessageProcessor(targetAppInstanceId)
                            val bytes = body.bytes()
                            val ciphertextMessageBytes = processor.encrypt(bytes)
                            context.body =
                                ByteArrayContent(ciphertextMessageBytes, contentType = ContentType.Application.Json)
                        }
                        is OutgoingContent.NoContent -> {
                            // GET / DELETE etc.: the `secure` header only tells the server to
                            // encrypt its response (handled by ClientDecryptPlugin). There is no
                            // request body to encrypt here, so this is a legal no-op.
                        }
                        else -> {
                            // Unknown body type. Fail loudly instead of silently sending plaintext
                            // under a `secure: 1` header — the API layer wraps these calls in
                            // safeApiCall, so this surfaces as ClientApiResult.EncryptFail to the
                            // caller without aborting the coroutine.
                            throw PasteException(
                                StandardErrorCode.ENCRYPT_FAIL.toErrorCode(),
                                "Unsupported content type for encryption: ${body::class}",
                            )
                        }
                    }
                }
            }
        }
    }
}
