package com.crosspaste.utils

import com.crosspaste.dao.sync.HostInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.Collections

actual fun getNetUtils(): NetUtils {
    return DesktopNetUtils
}

object DesktopNetUtils : NetUtils {

    val logger = KotlinLogging.logger {}

    private val hostListProvider = ValueProvider<List<HostInfo>>()

    private val en0IPAddressProvider = ValueProvider<String?>()

    override fun getHostInfoList(hostInfoFilter: (HostInfo) -> Boolean): List<HostInfo> {
        return hostListProvider.getValue {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .map { Pair(it.address, it.networkPrefixLength) }
                .filter { it.first is Inet4Address }
                .map {
                    HostInfo().apply {
                        networkPrefixLength = it.second
                        hostAddress = it.first.hostAddress
                    }
                }
                .filter(hostInfoFilter)
                .toList()
        } ?: listOf()
    }

    override fun hostPreFixMatch(
        host1: String,
        host2: String,
        prefixLength: Short,
    ): Boolean {
        // Convert host IP addresses to InetAddress objects
        val inetAddress1 = InetAddress.getByName(host1)
        val inetAddress2 = InetAddress.getByName(host2)

        // Convert InetAddress to byte arrays
        val bytes1 = inetAddress1.address
        val bytes2 = inetAddress2.address

        // Calculate subnet mask from prefixLength, ensuring it doesn't exceed 32 bits
        val maskLength = if (prefixLength > 32) 32 else prefixLength.toInt()
        val mask = -0x1 shl (32 - maskLength)

        // Convert byte arrays to integers and apply the subnet mask
        val addr1 = ByteBuffer.wrap(bytes1).int and mask
        val addr2 = ByteBuffer.wrap(bytes2).int and mask

        // Compare the masked addresses to check if they are in the same subnet
        return addr1 == addr2
    }

    override fun getEn0IPAddress(): String? {
        return en0IPAddressProvider.getValue {
            try {
                NetworkInterface.getNetworkInterfaces().asSequence()
                    .flatMap { Collections.list(it.inetAddresses).asSequence() }
                    .filter { addr ->
                        !addr.isLoopbackAddress &&
                            addr is Inet4Address &&
                            addr.isSiteLocalAddress
                    }
                    .map { it.hostAddress }
                    .firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }
}
