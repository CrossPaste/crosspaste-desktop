package com.clipevery.net

import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLBuilder

interface ClipClient {

    suspend fun post(
        urlBuilder: URLBuilder.(URLBuilder) -> Unit,
        message: ByteArray,
    ): HttpResponse

    suspend fun get(
        urlBuilder: URLBuilder.(URLBuilder) -> Unit
    ): HttpResponse
}
