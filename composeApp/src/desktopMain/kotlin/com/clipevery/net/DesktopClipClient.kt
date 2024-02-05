package com.clipevery.net

import com.clipevery.app.AppInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLBuilder
import io.ktor.util.InternalAPI

class DesktopClipClient(private val appInfo: AppInfo): ClipClient {

    private val client: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 1000
        }
        install(Logging)
    }

    @OptIn(InternalAPI::class)
    override suspend fun post(
        message: ByteArray,
        timeout: Long,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit
    ): HttpResponse {
        return client.post {
            header("appInstanceId", appInfo.appInstanceId)
            timeout {
                requestTimeoutMillis = timeout
            }
            body = message
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
