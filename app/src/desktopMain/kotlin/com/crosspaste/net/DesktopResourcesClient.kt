package com.crosspaste.net

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.InputStream
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DesktopResourcesClient : ResourcesClient {

    companion object {
        private val GOOGLE_URI = URI("https://www.google.com")
    }

    private val logger = KotlinLogging.logger {}

    private val noProxyClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    private val proxyClientMap: ConcurrentMap<Proxy, HttpClient> = ConcurrentMap()

    init {
        proxyClientMap[Proxy.NO_PROXY] = noProxyClient
        val proxy = DesktopProxy.getProxy(GOOGLE_URI)
        if (proxy != Proxy.NO_PROXY) {
            (proxy.address() as java.net.InetSocketAddress?)?.let { address ->
                proxyClientMap[proxy] =
                    HttpClient
                        .newBuilder()
                        .proxy(ProxySelector.of(address))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build()
            }
        }
    }

    override fun <T> request(
        url: String,
        success: (ClientResponse) -> T,
    ): T? =
        runCatching {
            proxyClientMap.entries.firstOrNull { it.key != Proxy.NO_PROXY }?.let {
                request(url, it.value, success)
            } ?: request(url, noProxyClient, success)
        }.getOrNull()

    override suspend fun <T> suspendRequest(
        url: String,
        success: suspend (ClientResponse) -> T,
    ): T? =
        runCatching {
            proxyClientMap.entries.firstOrNull { it.key != Proxy.NO_PROXY }?.let {
                suspendRequest(url, it.value, success)
            } ?: suspendRequest(url, noProxyClient, success)
        }.getOrElse {
            logger.warn { "Failed to fetch data from $url: ${it.message}" }
            null
        }

    private fun <T> request(
        url: String,
        client: HttpClient,
        success: (ClientResponse) -> T,
    ): T? {
        val uri = URI(url)

        val request = buildRequest(uri)

        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

        return if (response.statusCode() in 200..299) {
            success(DesktopClientResponse(response))
        } else {
            logger.warn { "Failed to fetch data from $url, status code: ${response.statusCode()}" }
            null
        }
    }

    private suspend fun <T> suspendRequest(
        url: String,
        client: HttpClient,
        success: suspend (ClientResponse) -> T,
    ): T? {
        val uri = URI(url)

        val request = buildRequest(uri)

        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

        return if (response.statusCode() in 200..299) {
            success(DesktopClientResponse(response))
        } else {
            logger.warn { "Failed to fetch data from $url, status code: ${response.statusCode()}" }
            null
        }
    }

    private fun buildRequest(uri: URI): HttpRequest =
        HttpRequest
            .newBuilder()
            .uri(uri)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            ).header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("DNT", "1")
            .timeout(Duration.ofSeconds(5))
            .build()
}

class DesktopClientResponse(
    private val response: HttpResponse<InputStream>,
) : ClientResponse {

    override fun getBody(): ByteReadChannel = ByteReadChannel(response.body().asSource().buffered())

    override fun getContentLength(): Long =
        response
            .headers()
            .firstValue("Content-Length")
            .orElse("-1")
            .toLong()
}
