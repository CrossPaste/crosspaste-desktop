package com.crosspaste.utils

import io.ktor.http.*

data class HostAndPort(
    val host: String,
    val port: Int,
)

fun URLBuilder.buildUrl(hostAndPort: HostAndPort) {
    this.protocol = URLProtocol.HTTP
    this.host = hostAndPort.host
    this.port = hostAndPort.port
}

fun URLBuilder.buildUrl(vararg paths: String) {
    this.path(*paths)
}
