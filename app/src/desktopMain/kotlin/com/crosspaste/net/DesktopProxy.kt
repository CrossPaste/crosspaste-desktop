package com.crosspaste.net

import com.crosspaste.utils.getSystemProperty
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.net.InetAddress
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
        return runCatching {
            val proxyList = ProxySelector.getDefault().select(uri)
            proxyList.firstOrNull { proxy ->
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
                    runCatching {
                        val proxyUri = URI(it)
                        val proxyHost = proxyUri.host
                        val proxyPort = proxyUri.port
                        val address = InetSocketAddress(proxyHost, proxyPort)
                        if (isProxyWorking(address)) {
                            Proxy(Proxy.Type.HTTP, address)
                        } else {
                            Proxy.NO_PROXY
                        }
                    }.getOrElse { e ->
                        logger.warn(e) { "Invalid proxy configuration in environment variable" }
                        Proxy.NO_PROXY
                    }
                } ?: Proxy.NO_PROXY
            }
        }.apply {
            systemProperty.set("java.net.useSystemProxies", "false")
        }.getOrElse {
            logger.warn(it) { "Failed to get proxy" }
            Proxy.NO_PROXY
        }
    }

    private fun isProxyWorking(address: InetSocketAddress): Boolean {
        return try {
            Socket().use { socket ->
                val inetAddress = InetAddress.getByName(address.hostName)
                val validAddress = InetSocketAddress(inetAddress, address.port)
                socket.connect(validAddress, 5000)
                true
            }
        } catch (e: IOException) {
            logger.warn(e) { "Proxy test failed" }
            false
        }
    }

    fun proxyToCommandLine(proxy: Proxy): String? {
        return when (proxy.type()) {
            Proxy.Type.DIRECT -> null
            Proxy.Type.HTTP -> {
                runCatching {
                    proxy.address()?.let { address ->
                        (address as? InetSocketAddress)?.let { inetSocketAddress ->
                            val port = inetSocketAddress.port
                            inetSocketAddress.hostName?.let {
                                "http://$it:$port"
                            } ?: run {
                                inetSocketAddress.address?.hostAddress?.let {
                                    "http://$it:$port"
                                }
                            }
                        }
                    }
                }.onFailure { e ->
                    logger.warn(e) { "Invalid proxy configuration" }
                }.getOrNull()
            }
            else -> null
        }
    }
}
