package com.crosspaste.net

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.extension.ProxyType
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.request
import io.ktor.client.statement.*
import io.ktor.http.headers
import io.ktor.util.collections.*

class DesktopResourcesClient(
    val configManager: DesktopConfigManager,
    userDataPathProvider: UserDataPathProvider,
) : AbstractResourcesClient(userDataPathProvider) {

    companion object {

        val fileUtils = getFileUtils()

        fun createClient(
            clientLogger: KLogger,
            proxyConfig: Proxy? = null,
        ): HttpClient =
            HttpClient(CIO) {
                followRedirects = true
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
                install(Logging, configure = {
                    logger =
                        object : Logger {
                            override fun log(message: String) {
                                clientLogger.info { message }
                            }
                        }
                })
            }
    }

    override val logger = KotlinLogging.logger {}

    private val noProxyClient = createClient(logger)

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
            proxyClientMap.getOrPut(proxy) {
                createClient(logger, proxy)
            }
        } ?: noProxyClient
    }

    override suspend fun clientRequest(
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
