package com.clipevery.net

import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

object DesktopProxy {

    private val proxySelector = ProxySelector.getDefault()

    fun getProxy(uri: URI): Proxy {
        val proxyList = proxySelector.select(uri)
        return proxyList.firstOrNull() ?: Proxy.NO_PROXY
    }
}
