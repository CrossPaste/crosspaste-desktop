package com.clipevery.net

import com.clipevery.app.AppInfo
import com.clipevery.utils.JsonUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.reflect.TypeInfo

class DesktopClipClient(private val appInfo: AppInfo): ClipClient {

    private val client: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 1000
        }
        install(Logging)
        install(ContentNegotiation) {
            json(JsonUtils.JSON, ContentType.Application.Json)
        }
    }

    override suspend fun <T: Any> post(
        message: T,
        messageType: TypeInfo,
        timeout: Long,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit
    ): HttpResponse {
        return client.post {
            header("appInstanceId", appInfo.appInstanceId)
            timeout {
                requestTimeoutMillis = timeout
            }
            contentType(ContentType.Application.Json)
            url {
                urlBuilder(this)
            }
            setBody(message, messageType)
        }
    }

    override suspend fun get(
        timeout: Long,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit,
    ): HttpResponse {
        return client.get {
            header("appInstanceId", appInfo.appInstanceId)
            timeout {
                requestTimeoutMillis = timeout
            }
            url {
                urlBuilder(this)
            }
        }
    }
}
