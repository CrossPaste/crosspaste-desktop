package com.crosspaste.net

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import okio.Path
import kotlin.coroutines.cancellation.CancellationException

abstract class AbstractResourcesClient(
    val userDataPathProvider: UserDataPathProvider,
    protected val fileUtils: FileUtils = getFileUtils(),
) : ResourcesClient {

    abstract val logger: KLogger

    abstract fun getHttpClient(): HttpClient

    protected open fun configureRequest(builder: HttpRequestBuilder) {}

    private fun getTempFilePath(): Path =
        userDataPathProvider.resolve(
            fileName = fileUtils.createRandomFileName(),
            appFileType = AppFileType.TEMP,
        )

    override suspend fun download(
        url: String,
        path: Path,
        listener: DownloadProgressListener,
    ) {
        var shouldShowProgress = false
        getHttpClient()
            .prepareRequest(url) {
                onDownload { bytesSent, contentLength ->
                    if (shouldShowProgress) {
                        listener.onProgress(bytesSent, contentLength)
                    }
                }
            }.execute { response ->
                if (response.status.isSuccess()) {
                    shouldShowProgress = true
                    val tempFilePath = getTempFilePath()
                    val downloadResult =
                        runCatching {
                            val channel = response.bodyAsChannel()
                            fileUtils.writeFile(tempFilePath, channel)
                            fileUtils.moveFile(tempFilePath, path).getOrThrow()
                        }
                    downloadResult
                        .onSuccess {
                            listener.onSuccess()
                        }.onFailure { error ->
                            deleteTempFile(tempFilePath)
                            listener.onFailure(response.status, error)
                            if (error is CancellationException) throw error
                        }
                } else {
                    listener.onFailure(response.status, null)
                }
            }
    }

    private fun deleteTempFile(tempFilePath: Path) {
        if (!fileUtils.existFile(tempFilePath)) return
        fileUtils.deleteFile(tempFilePath).onFailure { error ->
            logger.warn(error) { "Failed to delete temp file: $tempFilePath" }
        }
    }

    override suspend fun request(
        url: String,
        maxBytes: Long,
    ): Result<ClientResponse> =
        runCatching {
            require(maxBytes in 0 until Int.MAX_VALUE) {
                "maxBytes must be non-negative and fit in a ByteArray"
            }
            getHttpClient()
                .prepareRequest(url) {
                    configureRequest(this)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        logger.warn { "Failed to fetch data from $url, status code: ${response.status.value}" }
                        throw kotlin.Exception("HTTP error: ${response.status.value}")
                    }

                    val contentLength = response.contentLength()
                    if (contentLength != null && contentLength > maxBytes) {
                        throw ResourceResponseTooLargeException(maxBytes)
                    }

                    val body =
                        response
                            .bodyAsChannel()
                            .readRemaining(maxBytes + 1)
                            .readByteArray()
                    if (body.size.toLong() > maxBytes) {
                        throw ResourceResponseTooLargeException(maxBytes)
                    }
                    ClientResponse(body, response.contentType())
                }
        }.onFailure { e ->
            // Network/TLS failures (e.g. SunCertPathBuilderException behind a
            // TLS-intercepting proxy) must surface as Result.failure, not propagate:
            // callers rely on getOrNull()/onSuccess, and an escaping throw here would
            // crash the periodic update-check coroutine for the whole session.
            if (e is CancellationException) throw e
            logger.warn(e) { "Failed to request $url" }
        }
}
