package com.clipevery.net

import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

object DesktopProxy {

    init {
        System.setProperty("java.net.useSystemProxies", "true")
    }

    fun getProxy(uri: URI): Proxy {
        val proxyList = ProxySelector.getDefault().select(uri)
        return proxyList.firstOrNull() ?: Proxy.NO_PROXY
    }
}
