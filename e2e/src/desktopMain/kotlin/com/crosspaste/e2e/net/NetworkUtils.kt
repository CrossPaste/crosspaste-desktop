package com.crosspaste.e2e.net

import com.crosspaste.net.AbstractNetworkInterfaceService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    private val logger = KotlinLogging.logger {}

    /**
     * Enumerate usable LAN IPv4 addresses across all physical, up, non-loopback,
     * non-virtual interfaces. Each entry pairs the address with its network prefix
     * length (callers that only need the address can `.map { it.first }`).
     */
    fun enumerateLanInet4(): List<Pair<Inet4Address, Short>> {
        val nics =
            runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }
                .getOrElse {
                    logger.warn(it) { "Failed to enumerate network interfaces." }
                    emptyList()
                }
        return nics
            .asSequence()
            .filter { nic ->
                runCatching { nic.isUp && !nic.isLoopback && !nic.isVirtual }.getOrDefault(false)
            }.filterNot { nic ->
                AbstractNetworkInterfaceService.isLikelyVirtual(
                    nic.name,
                    AbstractNetworkInterfaceService.getMacAddress(nic),
                )
            }.flatMap { it.interfaceAddresses.asSequence() }
            .mapNotNull { ifAddr ->
                val addr = ifAddr.address
                if (addr is Inet4Address) addr to ifAddr.networkPrefixLength else null
            }.filterNot { (addr, _) ->
                addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress
            }.toList()
    }
}
