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
                requestTimeoutMillis = 1000
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
        timeout: Long = 1000L,
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

    suspend fun get(
        timeout: Long = 1000L,
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
}
