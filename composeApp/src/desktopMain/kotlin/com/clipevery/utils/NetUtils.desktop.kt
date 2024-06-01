package com.clipevery.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

actual fun getNetUtils(): NetUtils {
    return DesktopNetUtils
}

object DesktopNetUtils : NetUtils {

    val logger = KotlinLogging.logger {}

    private val hostListProvider = ValueProvider<List<String>>()

    private val en0IPAddressProvider = ValueProvider<String?>()

    override fun getHostList(): List<String> {
        return hostListProvider.getValue {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .flatMap { Collections.list(it.inetAddresses) }
                .filter { it.isSiteLocalAddress }
                .map { it.hostAddress }
        } ?: listOf()
    }

    override fun getEn0IPAddress(): String? {
        return en0IPAddressProvider.getValue {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { it.name.equals("en0", ignoreCase = true) }
                .flatMap { Collections.list(it.inetAddresses) }
                .filter { addr ->
                    addr is InetAddress &&
                        !addr.isLoopbackAddress &&
                        addr.hostAddress.indexOf(":") == -1
                }
                .map { it.hostAddress }
                .firstOrNull()
        }
    }
}
