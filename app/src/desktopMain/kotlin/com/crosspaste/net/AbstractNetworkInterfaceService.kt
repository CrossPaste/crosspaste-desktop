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

        // Known virtual network interface name prefixes (case-insensitive matching)
        internal val VIRTUAL_NIC_NAME_PREFIXES =
            listOf(
                "vmnet",
                "vmware", // VMware
                "vboxnet",
                "vbox", // VirtualBox
                "docker",
                "br-",
                "veth", // Docker
                "virbr", // libvirt/KVM
                "vethernet", // Hyper-V
                "tun",
                "tap", // VPN tunnels
                "wsl", // Windows Subsystem for Linux
                "ham", // Hamachi VPN
                "pangp", // Palo Alto GlobalProtect
                "utun", // macOS userspace tunnels
                "awdl", // Apple Wireless Direct Link
                "llw", // Apple Low Latency WLAN
                "bridge", // Generic bridge
                "anpi", // Apple Network Privacy Interface
            )

        // Known virtual NIC MAC address prefixes (OUI)
        internal val VIRTUAL_MAC_PREFIXES =
            listOf(
                "00:50:56",
                "00:0c:29",
                "00:05:69", // VMware
                "08:00:27", // VirtualBox
                "00:15:5d", // Hyper-V
                "02:42:", // Docker
                "52:54:00", // QEMU/KVM
                "00:16:3e", // Xen
            )

        fun isLikelyVirtualByName(name: String): Boolean {
            val lowerName = name.lowercase()
            return VIRTUAL_NIC_NAME_PREFIXES.any { lowerName.startsWith(it) }
        }

        fun isLikelyVirtualByMac(macAddress: String): Boolean {
            val lowerMac = macAddress.lowercase()
            return VIRTUAL_MAC_PREFIXES.any { lowerMac.startsWith(it) }
        }

        fun isLikelyVirtual(
            name: String,
            macAddress: String?,
        ): Boolean = isLikelyVirtualByName(name) || (macAddress != null && isLikelyVirtualByMac(macAddress))

        fun getMacAddress(nic: NetworkInterface): String? =
            runCatching {
                nic.hardwareAddress?.joinToString(":") { "%02x".format(it) }
            }.getOrNull()
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
                val macAddress = getMacAddress(nic)
                nic.interfaceAddresses.asSequence().map { Triple(it, nic.name, macAddress) }
            }.mapNotNull { (addr, nicName, macAddress) ->
                runCatching {
                    processAddress(addr, nicName, macAddress)
                }.onFailure { e ->
                    logger.warn(e) { "Failed to process address for interface: $nicName" }
                }.getOrNull()
            }

    protected fun processAddress(
        addr: InterfaceAddress,
        nicName: String,
        macAddress: String? = null,
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

        val likelyVirtual = isLikelyVirtual(nicName, macAddress)

        logger.info {
            "Local address found, Network interface: $nicName " +
                "address: $hostAddress networkPrefixLength: $networkPrefixLength" +
                " mac: $macAddress virtual: $likelyVirtual"
        }

        return NetworkInterfaceInfo(
            name = nicName,
            networkPrefixLength = networkPrefixLength,
            hostAddress = hostAddress,
            isLikelyVirtual = likelyVirtual,
        )
    }

    protected fun sortAddresses(addresses: List<NetworkInterfaceInfo>): List<NetworkInterfaceInfo> =
        addresses.sortedWith(
            compareBy<NetworkInterfaceInfo> { networkInterfaceInfo ->
                // Physical interfaces first, virtual interfaces last
                if (networkInterfaceInfo.isLikelyVirtual) 1 else 0
            }.thenByDescending { networkInterfaceInfo ->
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

    override fun getSortedNetworkInterfaceInfo(): List<NetworkInterfaceInfo> =
        sortAddresses(getAllNetworkInterfaceInfo())

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
