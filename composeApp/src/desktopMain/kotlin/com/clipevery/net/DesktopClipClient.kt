package com.clipevery.net

import com.clipevery.app.AppInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLBuilder
import io.ktor.util.InternalAPI

class DesktopClipClient(private val appInfo: AppInfo): ClipClient {

    private val client: HttpClient = HttpClient(CIO)

    @OptIn(InternalAPI::class)
    override suspend fun post(
        urlBuilder: URLBuilder.(URLBuilder) -> Unit,
        message: ByteArray,
    ): HttpResponse {
        return client.post {
            header("appInstanceId", appInfo.appInstanceId)
            body = message
        }
    }

    override suspend fun get(
        urlBuilder: URLBuilder.(URLBuilder) -> Unit
    ): HttpResponse {
        return client.get {
            header("appInstanceId", appInfo.appInstanceId)
            url {
                urlBuilder(this)
            }
        }
    }
}
