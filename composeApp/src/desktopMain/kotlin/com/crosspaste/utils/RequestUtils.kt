package com.crosspaste.utils

import io.ktor.http.*

fun buildUrl(
    urlBuilder: URLBuilder,
    host: String,
    port: Int,
) {
    urlBuilder.protocol = URLProtocol.HTTP
    urlBuilder.port = port
    urlBuilder.host = host
}

fun buildUrl(
    urlBuilder: URLBuilder,
    vararg paths: String,
) {
    urlBuilder.path(*paths)
}
