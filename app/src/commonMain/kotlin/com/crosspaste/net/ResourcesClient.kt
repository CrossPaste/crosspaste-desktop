package com.crosspaste.net

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.*

interface ResourcesClient {

    suspend fun request(url: String): Result<ClientResponse>
}

class ClientResponse(
    private val response: HttpResponse,
) {

    suspend fun getBody(): ByteReadChannel = response.bodyAsChannel()

    fun getContentLength(): Long = response.contentLength() ?: -1L
}
