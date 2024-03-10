package com.clipevery.net

import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLBuilder
import io.ktor.util.reflect.TypeInfo
import java.io.File

interface ClipClient {

    suspend fun <T: Any> post(
        message: T,
        messageType: TypeInfo,
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
