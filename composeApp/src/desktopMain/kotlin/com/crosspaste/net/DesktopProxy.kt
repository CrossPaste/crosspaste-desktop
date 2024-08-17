package com.crosspaste.net

import com.crosspaste.utils.getSystemProperty
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.URI

object DesktopProxy {

    private val logger: KLogger = KotlinLogging.logger {}

    private val systemProperty = getSystemProperty()

    fun getProxy(uri: URI): Proxy {
        systemProperty.set("java.net.useSystemProxies", "true")
        try {
            val proxyList = ProxySelector.getDefault().select(uri)
            return proxyList.firstOrNull { proxy ->
                proxy.type() == Proxy.Type.HTTP &&
                    proxy.address() != null &&
                    proxy.address() is InetSocketAddress &&
                    isProxyWorking(proxy.address() as InetSocketAddress)
            } ?: run {
                val proxyName =
                    if (uri.scheme.equals("https", ignoreCase = true)) {
                        "HTTPS_PROXY"
                    } else {
                        "HTTP_PROXY"
                    }
                System.getenv(proxyName)?.let {
                    try {
                        val proxyUri = URI(it)
                        val proxyHost = proxyUri.host
                        val proxyPort = proxyUri.port
                        val address = InetSocketAddress(proxyHost, proxyPort)
                        if (isProxyWorking(address)) {
                            Proxy(Proxy.Type.HTTP, address)
                        } else {
                            Proxy.NO_PROXY
                        }
                    } catch (e: Exception) {
                        logger.warn { "Invalid proxy configuration in environment variable: $e" }
                        Proxy.NO_PROXY
                    }
                } ?: Proxy.NO_PROXY
            }
        } finally {
            systemProperty.set("java.net.useSystemProxies", "false")
        }
    }

    fun isProxyWorking(address: InetSocketAddress): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(address, 5000) // 5 seconds timeout
                true
            }
        } catch (e: IOException) {
            logger.warn { "Proxy test failed: ${e.message}" }
            false
        }
    }

    fun proxyToCommandLine(proxy: Proxy): String? {
        return when (proxy.type()) {
            Proxy.Type.DIRECT -> null
            Proxy.Type.HTTP -> {
                try {
                    "http://${proxy.address()}:${(proxy.address() as InetSocketAddress).port}"
                } catch (e: Exception) {
                    logger.warn { "Invalid proxy configuration: $e" }
                    null
                }
            }
            else -> null
        }
    }
}
