package com.crosspaste.net

import io.ktor.utils.io.*

interface ResourcesClient {

    fun <T> request(
        url: String,
        success: (ClientResponse) -> T,
    ): T?

    suspend fun <T> suspendRequest(
        url: String,
        success: suspend (ClientResponse) -> T,
    ): T?
}

interface ClientResponse {

    fun getBody(): ByteReadChannel

    fun getContentLength(): Long
}
