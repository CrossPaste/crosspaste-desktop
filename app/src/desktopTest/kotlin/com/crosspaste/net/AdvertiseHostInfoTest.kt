package com.crosspaste.net

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The address a peer dialed is, by construction, an address it can route to — so
 * /sync/syncInfo must name it even when that interface is outside the discovery
 * selection (#4996).
 */
class AdvertiseHostInfoTest {

    private val home =
        NetworkInterfaceInfo(
            name = "eth0",
            networkPrefixLength = 24,
            hostAddress = "192.168.101.128",
        )

    // The shared side of a Windows Internet Connection Sharing setup. Auto-select
    // never picks it: its last octet is 1, which sorts last.
    private val shared =
        NetworkInterfaceInfo(
            name = "eth1",
            networkPrefixLength = 24,
            hostAddress = "192.168.137.1",
        )

    private fun service(
        all: List<NetworkInterfaceInfo>,
        selected: List<NetworkInterfaceInfo>,
    ) = TestNetworkInterfaceService(
        testNetworkInterfaces = all,
        testSelectedInterfaces = selected,
    )

    @Test
    fun `answers with the selected interface the peer dialed`() {
        val service = service(all = listOf(home, shared), selected = listOf(home))

        assertEquals(
            listOf(home.toHostInfo()),
            service.advertiseHostInfo("192.168.101.128"),
        )
    }

    @Test
    fun `answers with an unselected interface the peer dialed`() {
        val service = service(all = listOf(home, shared), selected = listOf(home))

        assertEquals(
            listOf(shared.toHostInfo()),
            service.advertiseHostInfo("192.168.137.1"),
        )
    }

    @Test
    fun `answers with nothing when discovery is off`() {
        val service = service(all = listOf(home, shared), selected = emptyList())

        assertEquals(emptyList(), service.advertiseHostInfo("192.168.137.1"))
    }

    @Test
    fun `answers with nothing for an address we do not own`() {
        val service = service(all = listOf(home, shared), selected = listOf(home))

        assertEquals(emptyList(), service.advertiseHostInfo("192.168.99.7"))
    }
}
