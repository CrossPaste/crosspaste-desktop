package com.clipevery.net

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import java.io.File

interface ClipClient {

    suspend fun <T: Any> post(
        message: T,
        messageType: TypeInfo,
        targetAppInstanceId: String? = null,
        encrypt: Boolean = false,
        timeout: Long = 1000L,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit
    ): HttpResponse

    suspend fun <T: Any>post(
        message: T,
        messageType: TypeInfo,
        files: List<File>,
        timeout: Long = 1000L,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit
    ): HttpResponse

    suspend fun get(
        timeout: Long = 1000L,
        urlBuilder: URLBuilder.(URLBuilder) -> Unit,
    ): HttpResponse
}
