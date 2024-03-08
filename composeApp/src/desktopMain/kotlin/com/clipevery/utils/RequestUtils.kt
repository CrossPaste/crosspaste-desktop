package com.clipevery.utils

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.path

fun buildUrl(urlBuilder: URLBuilder, host: String, port: Int, vararg paths: String) {
    urlBuilder.protocol = URLProtocol.HTTP
    urlBuilder.port = port
    urlBuilder.host = host
    urlBuilder.path(*paths)
}