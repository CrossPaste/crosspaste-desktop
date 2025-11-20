package com.crosspaste.net

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import okio.Path

abstract class AbstractResourcesClient(
    val userDataPathProvider: UserDataPathProvider,
) : ResourcesClient {

    companion object {
        val fileUtils = getFileUtils()
    }

    abstract val logger: KLogger

    abstract fun getHttpClient(): HttpClient

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
                    runCatching {
                        val channel = response.bodyAsChannel()
                        fileUtils.writeFile(tempFilePath, channel)
                        fileUtils.moveFile(tempFilePath, path)
                    }.onSuccess {
                        listener.onSuccess()
                    }.onFailure {
                        if (fileUtils.existFile(tempFilePath)) {
                            fileUtils.deleteFile(tempFilePath)
                        }
                        if (fileUtils.existFile(path)) {
                            fileUtils.deleteFile(path)
                        }
                        listener.onFailure(response.status, it)
                    }
                } else {
                    listener.onFailure(response.status, null)
                }
            }
    }

    override suspend fun request(url: String): Result<ClientResponse> {
        val response = clientRequest(url, getHttpClient())
        return if (response.status.isSuccess()) {
            Result.success(ClientResponse(response))
        } else {
            logger.warn { "Failed to fetch data from $url, status code: ${response.status.value}" }
            Result.failure(kotlin.Exception("HTTP error: ${response.status.value}"))
        }
    }

    abstract suspend fun clientRequest(
        url: String,
        client: HttpClient,
    ): HttpResponse
}
