package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.net.plugin.ClientDecryptPlugin
import com.crosspaste.net.plugin.ClientEncryptPlugin
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.reflect.*

class PasteClient(
    private val appInfo: AppInfo,
    private val clientEncryptPlugin: ClientEncryptPlugin,
    private val clientDecryptPlugin: ClientDecryptPlugin,
) {

    private val clientLogger = KotlinLogging.logger {}

    private val client: HttpClient =
        HttpClient(CIO) {
            install(DefaultRequest) {
                header(HttpHeaders.AcceptEncoding, "identity")
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 3000
            }
            install(Logging, configure = {
                logger =
                    object : Logger {
                        override fun log(message: String) {
                            clientLogger.debug { message }
                        }
                    }
            })
            install(ContentNegotiation) {
                json(getJsonUtils().JSON, ContentType.Application.Json)
            }
            install(clientEncryptPlugin)
            install(clientDecryptPlugin)
        }

    suspend fun <T : Any> post(
        message: T,
        messageType: TypeInfo,
        timeout: Long = 3000L,
        headersBuilder: (HeadersBuilder.() -> Unit) = {},
        urlBuilder: URLBuilder.() -> Unit,
    ): HttpResponse =
        client.post {
            header("appInstanceId", appInfo.appInstanceId)
            headers(headersBuilder)
            timeout {
                requestTimeoutMillis = timeout
            }
            contentType(ContentType.Application.Json)
            url {
                urlBuilder()
            }
            setBody(message, messageType)
        }

    /**
     * Sends a binary payload (chunk bytes, icon bytes, etc.). When the caller
     * sets the `secure: 1` header [ClientEncryptPlugin] intercepts the
     * [ByteArrayContent] at HttpSendPipeline.Before and replaces it with the
     * encrypted bytes (and rewrites Content-Type to application/json) — no
     * SecureStore plumbing needed at this layer.
     */
    suspend fun postBinary(
        bytes: ByteArray,
        timeout: Long = 30_000L,
        contentType: ContentType = ContentType.Application.OctetStream,
        headersBuilder: (HeadersBuilder.() -> Unit) = {},
        urlBuilder: URLBuilder.() -> Unit,
    ): HttpResponse =
        client.post {
            header("appInstanceId", appInfo.appInstanceId)
            headers(headersBuilder)
            timeout {
                requestTimeoutMillis = timeout
            }
            url {
                urlBuilder()
            }
            setBody(ByteArrayContent(bytes, contentType = contentType))
        }

    suspend fun get(
        timeout: Long = 3000L,
        headersBuilder: (HeadersBuilder.() -> Unit) = {},
        urlBuilder: URLBuilder.() -> Unit,
    ): HttpResponse =
        client.get {
            header("appInstanceId", appInfo.appInstanceId)
            headers(headersBuilder)
            timeout {
                requestTimeoutMillis = timeout
            }
            url {
                urlBuilder()
            }
        }

    fun close() {
        client.close()
    }
}
