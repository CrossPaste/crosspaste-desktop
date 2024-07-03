package com.crosspaste.net

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

object DesktopProxy {

    fun getProxy(uri: URI): Proxy {
        System.setProperty("java.net.useSystemProxies", "true")
        try {
            val proxyList = ProxySelector.getDefault().select(uri)
            return proxyList.firstOrNull { proxy ->
                proxy.type() == Proxy.Type.HTTP &&
                    proxy.address() != null &&
                    proxy.address() is InetSocketAddress
            } ?: run {
                val proxyName =
                    if (uri.scheme == "http") {
                        "HTTP_PROXY"
                    } else {
                        "HTTPS_PROXY"
                    }
                System.getenv(proxyName)?.let {
                    val proxyUri = URI(it)
                    val proxyHost = proxyUri.host
                    val proxyPort = proxyUri.port
                    Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
                } ?: run {
                    Proxy.NO_PROXY
                }
            }
        } finally {
            System.setProperty("java.net.useSystemProxies", "false")
        }
    }
}
