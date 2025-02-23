package com.crosspaste.utils

import com.crosspaste.utils.DesktopNetUtils.getAllLocalAddresses
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.net.Inet4Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NetworkUtilsTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(NetworkInterface::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        getNetUtils().clearProviderCache()
    }

    @Test
    fun testGetAllLocalAddresses() {
        // Use MockK to mock NetworkInterface and related classes
        mockkStatic(NetworkInterface::class)

        val nic1 = mockk<NetworkInterface>()
        val nic2 = mockk<NetworkInterface>()
        val nic3 = mockk<NetworkInterface>()

        every { nic1.isUp } returns true
        every { nic1.isLoopback } returns false
        every { nic1.isVirtual } returns false
        every { nic1.name } returns "eth0"

        every { nic2.isUp } returns true
        every { nic2.isLoopback } returns false
        every { nic2.isVirtual } returns false
        every { nic2.name } returns "wlan0"

        every { nic3.isUp } returns true
        every { nic3.isLoopback } returns false
        every { nic3.isVirtual } returns false
        every { nic3.name } returns "null0"

        val addr1 = mockk<InterfaceAddress>()
        val addr2 = mockk<InterfaceAddress>()
        val addr3 = mockk<InterfaceAddress>()
        val inetAddr1 = mockk<Inet4Address>()
        val inetAddr2 = mockk<Inet4Address>()

        every { addr1.address } returns inetAddr1
        every { addr2.address } returns inetAddr2
        every { addr3.address } returns null
        every { inetAddr1.hostAddress } returns "192.168.1.8"
        every { inetAddr2.hostAddress } returns "10.0.0.5"
        every { addr1.networkPrefixLength } returns 24
        every { addr2.networkPrefixLength } returns 16
        every { addr3.networkPrefixLength } returns 0

        every { nic1.interfaceAddresses } returns listOf(addr1)
        every { nic2.interfaceAddresses } returns listOf(addr2)
        every { nic3.interfaceAddresses } returns listOf(addr3)

        val networkInterfaces = Collections.enumeration(listOf(nic1, nic2, nic3))
        every { NetworkInterface.getNetworkInterfaces() } returns networkInterfaces

        // Execute the test
        val result = getAllLocalAddresses().toList()

        // Verify the results
        assertEquals(2, result.size)
        assertEquals("192.168.1.8", result[0].first.hostAddress)
        assertEquals(24, result[0].first.networkPrefixLength)
        assertEquals("eth0", result[0].second)
        assertEquals("10.0.0.5", result[1].first.hostAddress)
        assertEquals(16, result[1].first.networkPrefixLength)
        assertEquals("wlan0", result[1].second)

        // Verify that NetworkInterface.getNetworkInterfaces() was called
        verify { NetworkInterface.getNetworkInterfaces() }

        // Verify that the interface with null address was skipped
        assertFalse(result.any { it.second == "null0" })
    }

    @Test
    fun `getPreferredLocalIPAddress returns correct IP when valid interfaces exist`() {
        val nic1 = mockNetworkInterface("eth0", "192.168.1.100", 24)
        val nic2 = mockNetworkInterface("wlan0", "192.168.2.100", 24)

        every { NetworkInterface.getNetworkInterfaces() } returns Collections.enumeration(listOf(nic1, nic2))

        val result = DesktopNetUtils.getPreferredLocalIPAddress()

        assertEquals("192.168.1.100", result)
    }

    @Test
    fun `getPreferredLocalIPAddress returns null when no valid interfaces exist`() {
        val nic = mockNetworkInterface("lo", "127.0.0.1", 8)

        every { NetworkInterface.getNetworkInterfaces() } returns Collections.enumeration(listOf(nic))

        val result = DesktopNetUtils.getPreferredLocalIPAddress()

        assertNull(result)
    }

    @Test
    fun `getPreferredLocalIPAddress prefers eth interfaces over others`() {
        val nic1 = mockNetworkInterface("wlan0", "192.168.2.100", 24)
        val nic2 = mockNetworkInterface("eth0", "192.168.1.100", 24)

        every { NetworkInterface.getNetworkInterfaces() } returns Collections.enumeration(listOf(nic1, nic2))

        val result = DesktopNetUtils.getPreferredLocalIPAddress()

        assertEquals("192.168.1.100", result)
    }

    private fun mockNetworkInterface(
        name: String,
        ip: String,
        prefixLength: Short,
    ): NetworkInterface {
        return mockk<NetworkInterface>().apply {
            every { isUp } returns true
            every { isLoopback } returns false
            every { isVirtual } returns false
            every { this@apply.name } returns name
            every { interfaceAddresses } returns
                listOf(
                    mockk<InterfaceAddress>().apply {
                        every { address } returns
                            mockk<Inet4Address>().apply {
                                every { hostAddress } returns ip
                            }
                        every { networkPrefixLength } returns prefixLength
                    },
                )
        }
    }
}
