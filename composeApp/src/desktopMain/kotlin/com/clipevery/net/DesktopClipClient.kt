package com.clipevery.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.util.InternalAPI
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.signal.libsignal.protocol.SessionCipher
import java.io.ByteArrayOutputStream

class DesktopClipClient: ClipClient {

    val client: HttpClient = HttpClient(CIO)


    @OptIn(InternalAPI::class, ExperimentalSerializationApi::class)
    suspend inline fun <reified T : Any> post(url: String,
                                              appInstanceId: String,
                                              sessionCipher: SessionCipher,
                                              message: T): HttpResponse {
        return client.post(url) {
            header("appInstanceId", appInstanceId)
            val outputStream = ByteArrayOutputStream()
            Json.encodeToStream(message, outputStream)
            body = sessionCipher.encrypt(outputStream.toByteArray())
        }
    }

    @OptIn(InternalAPI::class, ExperimentalSerializationApi::class)
    suspend inline fun <reified T : Any> get(url: String,
                                             appInstanceId: String,
                                             sessionCipher: SessionCipher,
                                             message: T): HttpResponse {
        return client.get(url) {
            header("appInstanceId", appInstanceId)
            val outputStream = ByteArrayOutputStream()
            Json.encodeToStream(message, outputStream)
            body = sessionCipher.encrypt(outputStream.toByteArray())
        }
    }
}
