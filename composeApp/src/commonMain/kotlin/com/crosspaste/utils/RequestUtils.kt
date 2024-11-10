package com.crosspaste.utils

import io.ktor.http.*

fun URLBuilder.buildUrl(
    host: String,
    port: Int,
) {
    this.protocol = URLProtocol.HTTP
    this.port = port
    this.host = host
}

fun URLBuilder.buildUrl(vararg paths: String) {
    this.path(*paths)
}
