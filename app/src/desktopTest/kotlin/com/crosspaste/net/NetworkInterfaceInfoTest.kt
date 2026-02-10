package com.crosspaste.net

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkInterfaceInfoTest {

    @Test
    fun `toHostInfo converts networkPrefixLength and hostAddress`() {
        val info = NetworkInterfaceInfo("eth0", 24, "192.168.1.100")
        val hostInfo = info.toHostInfo()
        assertEquals(24.toShort(), hostInfo.networkPrefixLength)
        assertEquals("192.168.1.100", hostInfo.hostAddress)
    }

    @Test
    fun `toString formats as name dash address slash prefix`() {
        val info = NetworkInterfaceInfo("wlan0", 16, "10.0.0.1")
        assertEquals("wlan0 - 10.0.0.1/16", info.toString())
    }

    @Test
    fun `toHostInfo preserves different prefix lengths`() {
        assertEquals(8.toShort(), NetworkInterfaceInfo("lo", 8, "127.0.0.1").toHostInfo().networkPrefixLength)
        assertEquals(32.toShort(), NetworkInterfaceInfo("lo", 32, "127.0.0.1").toHostInfo().networkPrefixLength)
    }
}
