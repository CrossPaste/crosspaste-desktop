package com.crosspaste.net

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.*
import okio.Path

interface ResourcesClient {

    suspend fun request(url: String): Result<ClientResponse>

    suspend fun download(
        url: String,
        path: Path,
        listener: DownloadProgressListener,
    )
}

class ClientResponse(
    private val response: HttpResponse,
) {

    suspend fun getBody(): ByteReadChannel = response.bodyAsChannel()

    fun getContentLength(): Long = response.contentLength() ?: -1L
}

interface DownloadProgressListener {

    fun onFailure(
        httpStatusCode: HttpStatusCode,
        throwable: Throwable?,
    )

    fun onSuccess()

    fun onProgress(
        bytesRead: Long,
        contentLength: Long?,
    )
}
