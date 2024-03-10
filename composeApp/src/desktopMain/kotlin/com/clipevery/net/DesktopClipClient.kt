package com.clipevery.net

import com.clipevery.app.AppInfo
import com.clipevery.utils.JsonUtils
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.streams.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File

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

    @OptIn(InternalAPI::class)
    override suspend fun <T : Any> post(
        message: T,
        messageType: TypeInfo,
        files: List<File>,
        timeout: Long,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit
    ): HttpResponse {
        return client.post {
            header("appInstanceId", appInfo.appInstanceId)
            timeout {
                requestTimeoutMillis = timeout
            }
            contentType(ContentType.MultiPart.FormData)
            url {
                urlBuilder(this)
            }
            body = MultiPartFormDataContent(formData {
                val serializer = Json.serializersModule.serializer(messageType.type.java)
                append("json", JsonUtils.JSON.encodeToString(serializer, message), Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                })
                files.forEachIndexed { index, file ->
                    appendInput(
                        key = "file$index",
                        headers = Headers.build {
                            append(HttpHeaders.ContentDisposition, file.name)
                        }
                    ) {
                        file.inputStream().asInput()
                    }
                }
            })
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
