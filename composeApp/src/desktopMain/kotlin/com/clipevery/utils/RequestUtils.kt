package com.clipevery.utils

import com.clipevery.dao.sync.HostInfo
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.path

fun buildUrl(urlBuilder: URLBuilder, hostInfo: HostInfo, port: Int, vararg paths: String) {
    urlBuilder.protocol = URLProtocol.HTTP
    urlBuilder.port = port
    urlBuilder.host = hostInfo.hostAddress
    urlBuilder.path(*paths)
}