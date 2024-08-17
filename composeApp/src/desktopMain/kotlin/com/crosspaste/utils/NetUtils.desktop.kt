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

    private val preferredLocalIPAddress = ValueProvider<String?>()

    // Check if the given address is a private IP address
    private fun isPrivateAddress(
        address: InetAddress,
        prefixLength: Short,
    ): Boolean {
        if (address !is Inet4Address) return false
        val ip = address.address
        return when {
            prefixLength >= 8 &&
                ip[0] == 10.toByte() -> true // 10.0.0.0/8
            prefixLength >= 12 &&
                ip[0] == 172.toByte() &&
                ip[1].toInt() and 0xFF in 16..31 -> true // 172.16.0.0/12
            prefixLength >= 16 &&
                ip[0] == 192.toByte() &&
                ip[1] == 168.toByte() -> true // 192.168.0.0/16
            // Add other special-use addresses if needed
            else -> false
        }
    }

    // Get all potential local IP addresses
    private fun getAllLocalAddresses(): Sequence<Pair<HostInfo, String>> {
        return Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { nic ->
                nic.interfaceAddresses.asSequence().map { Pair(it, nic.name) }
            }
            .filter { (addr, _) -> addr.address is Inet4Address }
            .map { (addr, nicName) ->
                HostInfo().apply {
                    networkPrefixLength = addr.networkPrefixLength
                    hostAddress = addr.address.hostAddress
                } to nicName
            }
            .filter { (hostInfo, _) ->
                val address = InetAddress.getByName(hostInfo.hostAddress)
                isPrivateAddress(address, hostInfo.networkPrefixLength) &&
                    !hostInfo.hostAddress.endsWith(".0") &&
                    !hostInfo.hostAddress.endsWith(".1") &&
                    !hostInfo.hostAddress.endsWith(".255")
            }
    }

    // Sort the addresses based on preference
    private fun sortAddresses(addresses: Sequence<Pair<HostInfo, String>>): Sequence<Pair<HostInfo, String>> {
        return addresses.sortedWith(
            compareByDescending<Pair<HostInfo, String>> { (hostInfo, _) ->
                // Prefer smaller networks (larger networkPrefixLength)
                hostInfo.networkPrefixLength
            }.thenByDescending { (hostInfo, _) ->
                // Then sort by the last octet of the IP address
                hostInfo.hostAddress.split(".").last().toIntOrNull() ?: 0
            }.thenBy { (_, nicName) ->
                // Prefer network interfaces named eth* or en*
                if (nicName.startsWith("eth") || nicName.startsWith("en")) 0 else 1
            },
        )
    }

    override fun getHostInfoList(hostInfoFilter: (HostInfo) -> Boolean): List<HostInfo> {
        return hostListProvider.getValue {
            val list =
                sortAddresses(getAllLocalAddresses())
                    .map { it.first }
                    .filter(hostInfoFilter)
                    .toList()
            for (hostInfo in list) {
                logger.info { "Local IP address: ${hostInfo.hostAddress}" }
            }
            list
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

    // Get the preferred local IP address
    override fun getPreferredLocalIPAddress(): String? {
        return preferredLocalIPAddress.getValue {
            try {
                sortAddresses(getAllLocalAddresses())
                    .map { (hostInfo, _) -> hostInfo.hostAddress }
                    .firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }
}
