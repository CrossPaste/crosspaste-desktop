package com.clipevery.net

import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLBuilder
import io.ktor.util.reflect.TypeInfo

interface ClipClient {

    suspend fun <T: Any> post(
        message: T,
        messageType: TypeInfo,
        timeout: Long = 1000L,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit
    ): HttpResponse

    suspend fun get(
        timeout: Long = 1000L,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit,
    ): HttpResponse
}
