package com.crosspaste.net

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.reflect.*

interface PasteClient {

    suspend fun <T : Any> post(
        message: T,
        messageType: TypeInfo,
        targetAppInstanceId: String? = null,
        encrypt: Boolean = false,
        timeout: Long = 1000L,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit,
    ): HttpResponse

    suspend fun get(
        timeout: Long = 1000L,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit,
    ): HttpResponse
}
