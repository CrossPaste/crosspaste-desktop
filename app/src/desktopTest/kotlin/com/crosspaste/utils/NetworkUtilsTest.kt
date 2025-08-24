package com.crosspaste.utils

import com.crosspaste.net.NetworkInterfaceInfo
import com.crosspaste.net.TestNetworkInterfaceService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkUtilsTest {

    private fun createTestService(
        interfaces: List<NetworkInterfaceInfo> = emptyList(),
        preferredInterface: NetworkInterfaceInfo? = null,
    ) = TestNetworkInterfaceService(interfaces, preferredInterface)

    @Test
    fun testGetAllLocalAddresses() {
        // Create test service with expected network interfaces
        val expectedInterfaces =
            listOf(
                NetworkInterfaceInfo("eth0", 24, "192.168.1.8"),
                NetworkInterfaceInfo("wlan0", 16, "10.0.0.5"),
            )

        val networkInterfaceService = createTestService(interfaces = expectedInterfaces)

        // Execute the test
        val result = networkInterfaceService.getAllNetworkInterfaceInfo()

        // Verify the results
        assertEquals(2, result.size)
        assertEquals("192.168.1.8", result[0].hostAddress)
        assertEquals(24, result[0].networkPrefixLength)
        assertEquals("eth0", result[0].name)
        assertEquals("10.0.0.5", result[1].hostAddress)
        assertEquals(16, result[1].networkPrefixLength)
        assertEquals("wlan0", result[1].name)
    }

    @Test
    fun `getPreferredLocalIPAddress returns correct IP when valid interfaces exist`() {
        val expectedInterface = NetworkInterfaceInfo("eth0", 24, "192.168.1.100")
        val networkInterfaceService = createTestService(preferredInterface = expectedInterface)

        val result = networkInterfaceService.getPreferredNetworkInterface()

        assertEquals("192.168.1.100", result?.hostAddress)
    }

    @Test
    fun `getPreferredLocalIPAddress returns null when no valid interfaces exist`() {
        val networkInterfaceService = createTestService(preferredInterface = null)

        val result = networkInterfaceService.getPreferredNetworkInterface()

        assertNull(result)
    }

    @Test
    fun `getPreferredLocalIPAddress prefers eth interfaces over others`() {
        val expectedInterface = NetworkInterfaceInfo("eth0", 24, "192.168.1.100")
        val networkInterfaceService = createTestService(preferredInterface = expectedInterface)

        val result = networkInterfaceService.getPreferredNetworkInterface()

        assertEquals("192.168.1.100", result?.hostAddress)
    }
}
