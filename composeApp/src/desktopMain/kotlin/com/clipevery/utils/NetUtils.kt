package com.clipevery.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.net.Socket

fun telnet(host: String, port: Int, timeout: Int): Boolean {
    return try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeout)
            true
        }
    } catch (e: Exception) {
        false
    }
}

suspend fun telnet(hosts: List<String>, port: Int, timeout: Int): String? {
    val deferreds = coroutineScope {
        hosts.map { host ->
            async {
                if (telnet(host, port, timeout)) host else null
            }
        }
    }

    var result: String? = null
    while (deferreds.isNotEmpty() && result == null) {
        select {
            deferreds.forEach { deferred ->
                deferred.onAwait { hostResult ->
                    if (hostResult != null) {
                        result = hostResult
                        deferreds.forEach { it.cancel() }
                    }
                }
            }
        }
    }
    return result
}