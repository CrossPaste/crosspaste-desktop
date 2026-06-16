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

        // Legacy interface names: letters followed by a trailing numeric index (eth0, wlan0,
        // en1). The captured digits are the interface index. systemd predictable names
        // (enp0s3) have digits embedded mid-name and intentionally do NOT match.
        private val LEGACY_INDEXED_NAME_REGEX = Regex("^[a-zA-Z]+([0-9]+)$")

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

        // Known LAN-capable physical interface name prefixes (Wi-Fi + Ethernet),
        // covering both legacy names (eth0, wlan0) and systemd predictable names
        // (enp0s3, eno1, ens33, wlp2s0, wlx...). macOS uses en* for both Wi-Fi and Ethernet.
        internal val KNOWN_LAN_NIC_NAME_PREFIXES =
            listOf(
                "eth", // Linux legacy Ethernet
                // Short prefixes "en"/"em" can collide with unrelated names (e.g. some
                // tunnels). This is safe because interfaceTypeRank checks isLikelyVirtual
                // BEFORE isKnownLanByName, so virtual interfaces are still ranked down even
                // if their name starts with one of these. Keep that ordering if refactoring.
                "en", // macOS en* + systemd eno/enp/ens/enx
                "em", // some server Ethernet (em0)
                "wlan", // Linux legacy Wi-Fi
                "wlp",
                "wlx", // systemd predictable Wi-Fi
            )

        // Cellular / mobile-broadband interface name prefixes. These can never reach
        // LAN peers, so they must rank last for local-network device sync.
        internal val CELLULAR_NIC_NAME_PREFIXES =
            listOf(
                "rmnet", // Qualcomm (Android), covers rmnet_data4
                "rev_rmnet",
                "ccmni", // MediaTek (Android)
                "pdp_ip", // legacy Android cellular
                "clat", // 464XLAT clat daemon (cellular NAT64)
                "wwan", // WWAN modems (desktop/laptop)
                "wwp", // systemd predictable WWAN
            )

        fun isKnownLanByName(name: String): Boolean {
            val lowerName = name.lowercase()
            return KNOWN_LAN_NIC_NAME_PREFIXES.any { lowerName.startsWith(it) }
        }

        fun isLikelyCellularByName(name: String): Boolean {
            val lowerName = name.lowercase()
            return CELLULAR_NIC_NAME_PREFIXES.any { lowerName.startsWith(it) }
        }

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

    // Interface type ranking for a LAN sync app: known Wi-Fi/Ethernet first, then
    // unknown physical, then virtual, and cellular last (cellular can never reach
    // LAN peers, so selecting it makes the device undiscoverable).
    private fun interfaceTypeRank(info: NetworkInterfaceInfo): Int =
        when {
            isLikelyCellularByName(info.name) -> 3
            info.isLikelyVirtual -> 2
            isKnownLanByName(info.name) -> 0
            else -> 1
        }

    // Trailing numeric index of a legacy-style interface name (eth0 -> 0, eth1 -> 1,
    // wlan0 -> 0). Lower index is preferred so eth0/wlan0 win over eth1. Only legacy
    // "<letters><digits>" names carry a meaningful index; systemd predictable names
    // (enp0s3, ens33, wlp2s0) embed bus/slot numbers that are NOT interface indices, so
    // they default to 0 and let the later address-based keys decide.
    private fun interfaceIndex(name: String): Int =
        LEGACY_INDEXED_NAME_REGEX
            .matchEntire(name)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull() ?: 0

    // Preference among RFC1918 private ranges for a home/SOHO LAN sync app:
    // 192.168.x (home/SOHO) < 172.16-31.x < 10.x (corporate/VPN/carrier NAT) < other.
    private fun subnetPreferenceRank(hostAddress: String): Int {
        val octets = hostAddress.split(".")
        val first = octets.getOrNull(0)?.toIntOrNull()
        val second = octets.getOrNull(1)?.toIntOrNull()
        return when {
            first == 192 && second == 168 -> 0
            first == 172 && second != null && second in 16..31 -> 1
            first == 10 -> 2
            else -> 3
        }
    }

    private fun lastOctet(hostAddress: String): Int =
        hostAddress
            .split(".")
            .lastOrNull()
            ?.toIntOrNull() ?: Int.MIN_VALUE

    protected fun sortAddresses(addresses: List<NetworkInterfaceInfo>): List<NetworkInterfaceInfo> =
        addresses.sortedWith(
            // 1. Interface type: known Wi-Fi/Ethernet first, unknown next, virtual after,
            //    cellular last. This keeps wlan0/eth0 above rmnet_data4 etc.
            compareBy<NetworkInterfaceInfo> { interfaceTypeRank(it) }
                // 2. Lower interface index first (eth0 before eth1); eth0 and wlan0 tie here.
                .thenBy { interfaceIndex(it.name) }
                // 3. Prefer home/LAN private ranges: 192.168.x < 172.16-31.x < 10.x < other.
                .thenBy { subnetPreferenceRank(it.hostAddress) }
                // 4. Prefer smaller networks (larger networkPrefixLength).
                .thenByDescending { it.networkPrefixLength }
                // 5. Finally sort by the last octet of the IP address.
                .thenByDescending { lastOctet(it.hostAddress) },
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
