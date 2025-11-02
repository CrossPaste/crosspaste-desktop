package com.crosspaste.net

import com.crosspaste.app.AppFileType
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.extension.ProxyType
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.http
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.headers
import io.ktor.http.isSuccess
import io.ktor.util.collections.*
import okio.Path

class DesktopResourcesClient(
    val configManager: DesktopConfigManager,
    val userDataPathProvider: UserDataPathProvider,
) : ResourcesClient {

    companion object {

        val fileUtils = getFileUtils()

        fun createClient(proxyConfig: Proxy?): HttpClient =
            HttpClient(CIO) {
                engine {
                    proxyConfig?.let {
                        proxy =
                            if (proxyConfig.type == ProxyType.HTTP) {
                                ProxyBuilder.http("http://${proxyConfig.host}:${proxyConfig.port}")
                            } else {
                                ProxyBuilder.socks(proxyConfig.host, proxyConfig.port)
                            }
                    }
                }
            }
    }

    private val logger = KotlinLogging.logger {}

    private val noProxyClient = createClient(null)

    private val proxyClientMap: ConcurrentMap<Proxy, HttpClient> = ConcurrentMap()

    private fun getProxy(): Proxy? {
        val config = configManager.getCurrentConfig()
        return if (config.useManualProxy) {
            config.proxyPort.toIntOrNull()?.let {
                Proxy(config.proxyType, config.proxyHost, it)
            }
        } else {
            null
        }
    }

    private fun getHttpClient(): HttpClient {
        val proxy = getProxy()
        return proxy?.let {
            proxyClientMap.getOrPut(proxy) {
                createClient(proxy)
            }
        } ?: noProxyClient
    }

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

    private suspend fun clientRequest(
        url: String,
        client: HttpClient,
    ): HttpResponse =
        client.request(url) {
            headers {
                append(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                )
                append("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                append("Accept-Language", "en-US,en;q=0.9")
                append("DNT", "1")
            }
            timeout {
                requestTimeoutMillis = 5000L
            }
        }
}
