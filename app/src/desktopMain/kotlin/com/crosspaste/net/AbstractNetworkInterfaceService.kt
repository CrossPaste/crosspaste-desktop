package com.crosspaste.net

import com.crosspaste.utils.ValueProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.Inet4Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.Collections

abstract class AbstractNetworkInterfaceService : NetworkInterfaceService {

    companion object {
        private const val INVALID_END_OCTET_0 = ".0"
        private const val INVALID_END_OCTET_1 = ".1"
        private const val INVALID_END_OCTET_255 = ".255"
    }

    private val logger = KotlinLogging.logger {}

    protected val networkInterfaceInfoProvider = ValueProvider<List<NetworkInterfaceInfo>>()

    protected val preferredNetworkInterfaceInfo = ValueProvider<NetworkInterfaceInfo?>()

    protected fun isValidLocalAddress(hostAddress: String): Boolean =
        hostAddress.isNotEmpty() &&
            !hostAddress.endsWith(INVALID_END_OCTET_0) &&
            !hostAddress.endsWith(INVALID_END_OCTET_1) &&
            !hostAddress.endsWith(INVALID_END_OCTET_255)

    override fun getAllNetworkInterfaceInfo(): List<NetworkInterfaceInfo> =
        Collections
            .list(NetworkInterface.getNetworkInterfaces())
            .mapNotNull { nic ->
                runCatching {
                    if (!nic.isUp || nic.isLoopback || nic.isVirtual) {
                        null
                    } else {
                        nic
                    }
                }.onFailure { e ->
                    logger.warn(e) { "Failed to check network interface status: ${nic.name}" }
                }.getOrNull()
            }.flatMap { nic ->
                nic.interfaceAddresses.asSequence().map { Pair(it, nic.name) }
            }.mapNotNull { (addr, nicName) ->
                runCatching {
                    processAddress(addr, nicName)
                }.onFailure { e ->
                    logger.warn(e) { "Failed to process address for interface: $nicName" }
                }.getOrNull()
            }

    protected fun processAddress(
        addr: InterfaceAddress,
        nicName: String,
    ): NetworkInterfaceInfo? {
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

        return NetworkInterfaceInfo(
            name = nicName,
            networkPrefixLength = networkPrefixLength,
            hostAddress = hostAddress,
        )
    }

    protected fun sortAddresses(addresses: List<NetworkInterfaceInfo>): List<NetworkInterfaceInfo> =
        addresses.sortedWith(
            compareByDescending<NetworkInterfaceInfo> { networkInterfaceInfo ->
                // Prefer smaller networks (larger networkPrefixLength)
                networkInterfaceInfo.networkPrefixLength
            }.thenByDescending { networkInterfaceInfo ->
                // Then sort by the last octet of the IP address
                networkInterfaceInfo.hostAddress
                    .split(".")
                    .lastOrNull()
                    ?.toIntOrNull() ?: Int.MIN_VALUE
            }.thenBy { networkInterfaceInfo ->
                // Prefer network interfaces named eth* or en*
                when {
                    networkInterfaceInfo.name.startsWith("eth") -> 0
                    networkInterfaceInfo.name.startsWith("en") -> 1
                    else -> 2
                }
            },
        )

    override fun getPreferredNetworkInterface(): NetworkInterfaceInfo? =
        preferredNetworkInterfaceInfo.getValue {
            runCatching {
                sortAddresses(getAllNetworkInterfaceInfo())
                    .firstOrNull()
            }.getOrNull()
        }

    override fun clearProviderCache() {
        networkInterfaceInfoProvider.clear()
        preferredNetworkInterfaceInfo.clear()
    }
}
