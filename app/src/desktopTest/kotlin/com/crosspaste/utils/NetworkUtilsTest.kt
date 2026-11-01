package com.crosspaste.utils

import com.crosspaste.net.NetworkInterfaceInfo
import com.crosspaste.net.TestNetworkInterfaceService
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkUtilsTest {

    private val service = TestNetworkInterfaceService()

    @Test
    fun `known LAN interfaces sort before virtual and cellular ones`() {
        val ethernet = NetworkInterfaceInfo("eth0", 24, "192.168.1.8")
        val virtual = NetworkInterfaceInfo("docker0", 16, "172.17.0.1", isLikelyVirtual = true)
        val cellular = NetworkInterfaceInfo("rmnet_data0", 24, "10.20.30.40")

        assertEquals(
            listOf(ethernet, virtual, cellular),
            service.testSortAddresses(listOf(cellular, virtual, ethernet)),
        )
    }

    @Test
    fun `home LAN range is preferred over corporate range on the same interface type`() {
        val home = NetworkInterfaceInfo("eth0", 24, "192.168.1.8")
        val corporate = NetworkInterfaceInfo("eth0", 24, "10.0.0.5")

        assertEquals(
            listOf(home, corporate),
            service.testSortAddresses(listOf(corporate, home)),
        )
    }
}
