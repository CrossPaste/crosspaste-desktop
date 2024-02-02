package com.clipevery.net

import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLBuilder

interface ClipClient {

    suspend fun post(
        message: ByteArray,
        timeout: Long = 1000L,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit
    ): HttpResponse

    suspend fun get(
        timeout: Long = 1000L,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit,
    ): HttpResponse
}
