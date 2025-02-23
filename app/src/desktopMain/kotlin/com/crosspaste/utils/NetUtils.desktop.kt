package com.crosspaste.utils

import com.crosspaste.db.sync.HostInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.nio.ByteBuffer
import java.util.Collections

actual fun getNetUtils(): NetUtils {
    return DesktopNetUtils
}

object DesktopNetUtils : NetUtils {

    private val logger = KotlinLogging.logger {}

    private const val INVALID_END_OCTET_0 = ".0"
    private const val INVALID_END_OCTET_1 = ".1"
    private const val INVALID_END_OCTET_255 = ".255"

    private val hostListProvider = ValueProvider<List<HostInfo>>()

    private val preferredLocalIPAddress = ValueProvider<String?>()

    private fun isValidLocalAddress(hostAddress: String): Boolean =
        hostAddress.isNotEmpty() &&
            !hostAddress.endsWith(INVALID_END_OCTET_0) &&
            !hostAddress.endsWith(INVALID_END_OCTET_1) &&
            !hostAddress.endsWith(INVALID_END_OCTET_255)

    // Get all potential local IP addresses
    fun getAllLocalAddresses(): Sequence<Pair<HostInfo, String>> {
        return Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .mapNotNull { nic ->
                try {
                    if (!nic.isUp || nic.isLoopback || nic.isVirtual) {
                        null
                    } else {
                        nic
                    }
                } catch (e: SocketException) {
                    logger.warn(e) { "Failed to check network interface status: ${nic.name}" }
                    null
                }
            }
            .flatMap { nic ->
                nic.interfaceAddresses.asSequence().map { Pair(it, nic.name) }
            }
            .mapNotNull { (addr, nicName) ->
                try {
                    processAddress(addr, nicName)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to process address for interface: $nicName" }
                    null
                }
            }
    }

    private fun processAddress(
        addr: InterfaceAddress,
        nicName: String,
    ): Pair<HostInfo, String>? {
        val address = addr.address
        if (address !is Inet4Address) {
            return null
        }

        val hostAddress = address.hostAddress ?: return null
        val networkPrefixLength = addr.networkPrefixLength

        if (!isValidLocalAddress(hostAddress)) {
            logger.debug {
                "Not a local address, Network interface: $nicName " +
                    "address: $hostAddress networkPrefixLength: $networkPrefixLength"
            }
            return null
        }

        logger.info {
            "Local address found, Network interface: $nicName " +
                "address: $hostAddress networkPrefixLength: $networkPrefixLength"
        }

        return HostInfo(networkPrefixLength, hostAddress) to nicName
    }

    // Sort the addresses based on preference
    private fun sortAddresses(addresses: Sequence<Pair<HostInfo, String>>): Sequence<Pair<HostInfo, String>> {
        return addresses.sortedWith(
            compareByDescending<Pair<HostInfo, String>> { (hostInfo, _) ->
                // Prefer smaller networks (larger networkPrefixLength)
                hostInfo.networkPrefixLength
            }.thenByDescending { (hostInfo, _) ->
                // Then sort by the last octet of the IP address
                hostInfo.hostAddress.split(".").lastOrNull()?.toIntOrNull() ?: Int.MIN_VALUE
            }.thenBy { (_, nicName) ->
                // Prefer network interfaces named eth* or en*
                when {
                    nicName.startsWith("eth") -> 0
                    nicName.startsWith("en") -> 1
                    else -> 2
                }
            },
        )
    }

    override fun getHostInfoList(hostInfoFilter: HostInfoFilter): List<HostInfo> {
        val list =
            hostListProvider.getValue {
                sortAddresses(getAllLocalAddresses())
                    .map { it.first }
                    .toList()
            } ?: listOf()
        val hostInfoList = list.filter(hostInfoFilter::filter)
        logger.info { "getHostInfoList: $hostInfoList" }
        return hostInfoList
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
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun clearProviderCache() {
        hostListProvider.clear()
        preferredLocalIPAddress.clear()
    }
}
