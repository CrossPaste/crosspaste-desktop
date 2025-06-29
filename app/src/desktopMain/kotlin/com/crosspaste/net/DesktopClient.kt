package com.crosspaste.net

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import java.io.InputStream
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object DesktopClient {

    private val logger = KotlinLogging.logger {}

    private val GOOGLE_URI = URI("https://www.google.com")

    private val noProxyClient =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    private val proxyClientMap: ConcurrentMap<Proxy, HttpClient> = ConcurrentMap()

    init {
        proxyClientMap[Proxy.NO_PROXY] = noProxyClient
        val proxy = DesktopProxy.getProxy(GOOGLE_URI)
        if (proxy != Proxy.NO_PROXY) {
            (proxy.address() as java.net.InetSocketAddress?)?.let { address ->
                proxyClientMap[proxy] =
                    HttpClient.newBuilder()
                        .proxy(ProxySelector.of(address))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build()
            }
        }
    }

    fun <T> request(
        url: String,
        success: (HttpResponse<InputStream>) -> T,
    ): T? {
        return runCatching {
            proxyClientMap.entries.firstOrNull { it.key != Proxy.NO_PROXY }?.let {
                request(url, it.value, success)
            } ?: request(url, noProxyClient, success)
        }.getOrNull()
    }

    private fun <T> request(
        url: String,
        client: HttpClient,
        success: (HttpResponse<InputStream>) -> T,
    ): T? {
        val uri = URI(url)

        val request =
            HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(5))
                .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

        return if (response.statusCode() in 200..299) {
            success(response)
        } else {
            logger.warn { "Failed to fetch data from $url, status code: ${response.statusCode()}" }
            null
        }
    }
}
