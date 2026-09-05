package com.crosspaste.net

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.extension.ProxyType
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.headers
import io.ktor.util.collections.*

class DesktopResourcesClient(
    val configManager: DesktopConfigManager,
    userDataPathProvider: UserDataPathProvider,
    private val clientFactory: (KLogger, Proxy?) -> HttpClient = ::createClient,
) : AbstractResourcesClient(userDataPathProvider) {

    companion object {

        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun createClient(
            clientLogger: KLogger,
            proxyConfig: Proxy? = null,
        ): HttpClient =
            if (proxyConfig?.type == ProxyType.SOCKS) {
                HttpClient(OkHttp) {
                    configureClient(clientLogger)
                    engine {
                        proxy = ProxyBuilder.socks(proxyConfig.host, proxyConfig.port)
                    }
                }
            } else {
                HttpClient(CIO) {
                    configureClient(clientLogger)
                    engine {
                        proxyConfig?.let {
                            proxy = ProxyBuilder.http("http://${proxyConfig.host}:${proxyConfig.port}")
                        }
                    }
                }
            }

        private fun HttpClientConfig<*>.configureClient(clientLogger: KLogger) {
            followRedirects = true
            install(Logging) {
                logger =
                    object : Logger {
                        override fun log(message: String) {
                            clientLogger.info { message }
                        }
                    }
            }
        }
    }

    override val logger = KotlinLogging.logger {}

    private val noProxyClient = clientFactory(logger, null)

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

    override fun getHttpClient(): HttpClient {
        val proxy = getProxy()
        return proxy?.let {
            proxyClientMap.computeIfAbsent(proxy) {
                clientFactory(logger, proxy)
            }
        } ?: noProxyClient
    }

    override fun configureRequest(builder: HttpRequestBuilder) {
        builder.apply {
            headers {
                append("User-Agent", DEFAULT_USER_AGENT)
                append("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                append("Accept-Language", "en-US,en;q=0.9")
                append("DNT", "1")
            }
            timeout {
                requestTimeoutMillis = 5000L
            }
        }
    }

    override fun close() {
        noProxyClient.close()
        proxyClientMap.values.forEach { it.close() }
        proxyClientMap.clear()
    }
}
