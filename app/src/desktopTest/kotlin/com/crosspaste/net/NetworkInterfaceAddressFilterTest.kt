package com.crosspaste.net

import io.mockk.every
import io.mockk.mockk
import java.net.InetAddress
import java.net.InterfaceAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Interface enumeration yields unicast addresses this host owns; none of them may be
 * dropped on a last-octet heuristic. `.1` is the hotspot host address, and `.0`/`.255`
 * are ordinary hosts once the network is wider than /24.
 */
class NetworkInterfaceAddressFilterTest {

    private val service = TestNetworkInterfaceService()

    private fun interfaceAddress(
        host: String,
        prefixLength: Short,
    ): InterfaceAddress =
        mockk<InterfaceAddress> {
            every { address } returns InetAddress.getByName(host)
            every { networkPrefixLength } returns prefixLength
        }

    @Test
    fun `ordinary host address is kept`() {
        val info = service.testProcessAddress(interfaceAddress("192.168.1.100", 24), "en0")
        assertEquals(NetworkInterfaceInfo("en0", 24, "192.168.1.100"), info)
    }

    @Test
    fun `hotspot host address ending in dot 1 is kept`() {
        val info = service.testProcessAddress(interfaceAddress("192.168.137.1", 24), "wlan1")
        assertEquals("192.168.137.1", info?.hostAddress)
    }

    @Test
    fun `dot 0 and dot 255 hosts inside a slash 16 are kept`() {
        assertEquals(
            "10.1.5.0",
            service.testProcessAddress(interfaceAddress("10.1.5.0", 16), "eth0")?.hostAddress,
        )
        assertEquals(
            "10.1.7.255",
            service.testProcessAddress(interfaceAddress("10.1.7.255", 16), "eth0")?.hostAddress,
        )
    }

    @Test
    fun `ipv6 addresses are still ignored`() {
        assertNull(service.testProcessAddress(interfaceAddress("fe80::1", 64), "en0"))
    }
}
