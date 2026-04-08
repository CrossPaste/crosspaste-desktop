package com.crosspaste.net

import com.crosspaste.net.AbstractNetworkInterfaceService.Companion.isLikelyVirtual
import com.crosspaste.net.AbstractNetworkInterfaceService.Companion.isLikelyVirtualByMac
import com.crosspaste.net.AbstractNetworkInterfaceService.Companion.isLikelyVirtualByName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VirtualNetworkInterfaceDetectionTest {

    // ===== Name-based detection tests =====

    @Test
    fun `VMware interfaces detected as virtual by name`() {
        assertTrue(isLikelyVirtualByName("vmnet1"))
        assertTrue(isLikelyVirtualByName("vmnet8"))
        assertTrue(isLikelyVirtualByName("VMnet0"))
        assertTrue(isLikelyVirtualByName("VMware Network Adapter VMnet8"))
    }

    @Test
    fun `VirtualBox interfaces detected as virtual by name`() {
        assertTrue(isLikelyVirtualByName("vboxnet0"))
        assertTrue(isLikelyVirtualByName("vboxnet1"))
        assertTrue(isLikelyVirtualByName("VBoxNet2"))
    }

    @Test
    fun `Docker interfaces detected as virtual by name`() {
        assertTrue(isLikelyVirtualByName("docker0"))
        assertTrue(isLikelyVirtualByName("br-abcdef123456"))
        assertTrue(isLikelyVirtualByName("veth1234abc"))
    }

    @Test
    fun `Hyper-V interfaces detected as virtual by name`() {
        assertTrue(isLikelyVirtualByName("vEthernet (Default Switch)"))
    }

    @Test
    fun `VPN tunnel interfaces detected as virtual by name`() {
        assertTrue(isLikelyVirtualByName("tun0"))
        assertTrue(isLikelyVirtualByName("tap0"))
        assertTrue(isLikelyVirtualByName("utun3"))
        assertTrue(isLikelyVirtualByName("pangp0"))
    }

    @Test
    fun `WSL interfaces detected as virtual by name`() {
        assertTrue(isLikelyVirtualByName("wsl"))
        assertTrue(isLikelyVirtualByName("WSL"))
    }

    @Test
    fun `macOS special interfaces detected as virtual by name`() {
        assertTrue(isLikelyVirtualByName("awdl0"))
        assertTrue(isLikelyVirtualByName("llw0"))
        assertTrue(isLikelyVirtualByName("bridge0"))
        assertTrue(isLikelyVirtualByName("anpi0"))
    }

    @Test
    fun `libvirt bridge detected as virtual by name`() {
        assertTrue(isLikelyVirtualByName("virbr0"))
    }

    @Test
    fun `Hamachi VPN detected as virtual by name`() {
        assertTrue(isLikelyVirtualByName("ham0"))
    }

    @Test
    fun `physical interfaces not detected as virtual by name`() {
        assertFalse(isLikelyVirtualByName("eth0"))
        assertFalse(isLikelyVirtualByName("eth1"))
        assertFalse(isLikelyVirtualByName("en0"))
        assertFalse(isLikelyVirtualByName("en1"))
        assertFalse(isLikelyVirtualByName("wlan0"))
        assertFalse(isLikelyVirtualByName("wlp3s0"))
        assertFalse(isLikelyVirtualByName("enp0s3"))
        assertFalse(isLikelyVirtualByName("Ethernet"))
        assertFalse(isLikelyVirtualByName("Wi-Fi"))
    }

    // ===== MAC-based detection tests =====

    @Test
    fun `VMware MAC addresses detected as virtual`() {
        assertTrue(isLikelyVirtualByMac("00:50:56:c0:00:08"))
        assertTrue(isLikelyVirtualByMac("00:0c:29:ab:cd:ef"))
        assertTrue(isLikelyVirtualByMac("00:05:69:12:34:56"))
    }

    @Test
    fun `VirtualBox MAC addresses detected as virtual`() {
        assertTrue(isLikelyVirtualByMac("08:00:27:ab:cd:ef"))
    }

    @Test
    fun `Hyper-V MAC addresses detected as virtual`() {
        assertTrue(isLikelyVirtualByMac("00:15:5d:ab:cd:ef"))
    }

    @Test
    fun `Docker MAC addresses detected as virtual`() {
        assertTrue(isLikelyVirtualByMac("02:42:ac:11:00:02"))
    }

    @Test
    fun `QEMU KVM MAC addresses detected as virtual`() {
        assertTrue(isLikelyVirtualByMac("52:54:00:ab:cd:ef"))
    }

    @Test
    fun `Xen MAC addresses detected as virtual`() {
        assertTrue(isLikelyVirtualByMac("00:16:3e:ab:cd:ef"))
    }

    @Test
    fun `common physical MAC addresses not detected as virtual`() {
        assertFalse(isLikelyVirtualByMac("d4:5d:64:ab:cd:ef")) // Intel
        assertFalse(isLikelyVirtualByMac("3c:22:fb:ab:cd:ef")) // Apple
        assertFalse(isLikelyVirtualByMac("a4:83:e7:ab:cd:ef")) // Realtek
        assertFalse(isLikelyVirtualByMac("00:1a:2b:3c:4d:5e")) // Generic
    }

    @Test
    fun `MAC detection is case-insensitive`() {
        assertTrue(isLikelyVirtualByMac("00:50:56:C0:00:08"))
        assertTrue(isLikelyVirtualByMac("08:00:27:AB:CD:EF"))
    }

    // ===== Combined detection tests =====

    @Test
    fun `isLikelyVirtual detects by name alone`() {
        assertTrue(isLikelyVirtual("vmnet1", null))
        assertTrue(isLikelyVirtual("docker0", null))
    }

    @Test
    fun `isLikelyVirtual detects by MAC alone`() {
        assertTrue(isLikelyVirtual("unknown0", "00:50:56:c0:00:08"))
        assertTrue(isLikelyVirtual("Ethernet 2", "08:00:27:ab:cd:ef"))
    }

    @Test
    fun `isLikelyVirtual detects by either name or MAC`() {
        assertTrue(isLikelyVirtual("vmnet1", "00:50:56:c0:00:08"))
        assertTrue(isLikelyVirtual("vmnet1", "d4:5d:64:ab:cd:ef"))
        assertTrue(isLikelyVirtual("eth0", "00:50:56:c0:00:08"))
    }

    @Test
    fun `isLikelyVirtual returns false for physical interface`() {
        assertFalse(isLikelyVirtual("eth0", "d4:5d:64:ab:cd:ef"))
        assertFalse(isLikelyVirtual("en0", "3c:22:fb:ab:cd:ef"))
        assertFalse(isLikelyVirtual("wlan0", null))
    }
}

class NetworkInterfaceSortingTest {

    private val service = TestNetworkInterfaceService()

    private fun sort(addresses: List<NetworkInterfaceInfo>): List<NetworkInterfaceInfo> =
        service.testSortAddresses(addresses)

    @Test
    fun `physical interfaces sorted before virtual interfaces`() {
        val physical = NetworkInterfaceInfo("eth0", 24, "192.168.1.100", isLikelyVirtual = false)
        val virtual = NetworkInterfaceInfo("vmnet8", 24, "192.168.200.1", isLikelyVirtual = true)

        val sorted = sort(listOf(virtual, physical))

        assertEquals("eth0", sorted[0].name)
        assertEquals("vmnet8", sorted[1].name)
    }

    @Test
    fun `virtual interface with better subnet still sorted after physical`() {
        val physical = NetworkInterfaceInfo("eth0", 16, "10.0.0.50", isLikelyVirtual = false)
        val virtual = NetworkInterfaceInfo("vmnet8", 24, "192.168.200.100", isLikelyVirtual = true)

        val sorted = sort(listOf(virtual, physical))

        assertEquals("eth0", sorted[0].name)
        assertEquals("vmnet8", sorted[1].name)
    }

    @Test
    fun `among physical interfaces sorting by prefix length then octet then name`() {
        val eth0 = NetworkInterfaceInfo("eth0", 24, "192.168.1.50", isLikelyVirtual = false)
        val en0 = NetworkInterfaceInfo("en0", 24, "192.168.1.100", isLikelyVirtual = false)
        val wlan0 = NetworkInterfaceInfo("wlan0", 16, "10.0.0.200", isLikelyVirtual = false)

        val sorted = sort(listOf(wlan0, en0, eth0))

        // eth0 and en0 both /24, en0 has higher last octet -> en0 first among /24
        // then eth0, then wlan0 (/16)
        assertEquals("en0", sorted[0].name)
        assertEquals("eth0", sorted[1].name)
        assertEquals("wlan0", sorted[2].name)
    }

    @Test
    fun `among virtual interfaces same sorting rules apply`() {
        val vmnet1 = NetworkInterfaceInfo("vmnet1", 24, "192.168.100.1", isLikelyVirtual = true)
        val docker0 = NetworkInterfaceInfo("docker0", 16, "172.17.0.1", isLikelyVirtual = true)

        val sorted = sort(listOf(docker0, vmnet1))

        assertEquals("vmnet1", sorted[0].name)
        assertEquals("docker0", sorted[1].name)
    }

    @Test
    fun `mixed scenario with multiple physical and virtual interfaces`() {
        val interfaces =
            listOf(
                NetworkInterfaceInfo("vmnet8", 24, "192.168.200.1", isLikelyVirtual = true),
                NetworkInterfaceInfo("eth0", 24, "192.168.1.100", isLikelyVirtual = false),
                NetworkInterfaceInfo("docker0", 16, "172.17.0.1", isLikelyVirtual = true),
                NetworkInterfaceInfo("en0", 24, "192.168.1.50", isLikelyVirtual = false),
                NetworkInterfaceInfo("vboxnet0", 24, "192.168.56.1", isLikelyVirtual = true),
            )

        val sorted = sort(interfaces)

        // Physical first: eth0 (last octet 100) > en0 (last octet 50)
        assertFalse(sorted[0].isLikelyVirtual)
        assertFalse(sorted[1].isLikelyVirtual)
        assertEquals("eth0", sorted[0].name)
        assertEquals("en0", sorted[1].name)

        // Virtual after: all /24 virtual by octet, then /16
        assertTrue(sorted[2].isLikelyVirtual)
        assertTrue(sorted[3].isLikelyVirtual)
        assertTrue(sorted[4].isLikelyVirtual)
    }

    @Test
    fun `all virtual interfaces still returns a result`() {
        val interfaces =
            listOf(
                NetworkInterfaceInfo("vmnet8", 24, "192.168.200.1", isLikelyVirtual = true),
                NetworkInterfaceInfo("docker0", 16, "172.17.0.1", isLikelyVirtual = true),
            )

        val sorted = sort(interfaces)

        assertEquals(2, sorted.size)
        assertEquals("vmnet8", sorted[0].name)
    }

    @Test
    fun `single physical interface selected over multiple virtual`() {
        val interfaces =
            listOf(
                NetworkInterfaceInfo("vmnet1", 24, "192.168.100.1", isLikelyVirtual = true),
                NetworkInterfaceInfo("vmnet8", 24, "192.168.200.1", isLikelyVirtual = true),
                NetworkInterfaceInfo("docker0", 16, "172.17.0.1", isLikelyVirtual = true),
                NetworkInterfaceInfo("wlan0", 24, "192.168.1.50", isLikelyVirtual = false),
            )

        val sorted = sort(interfaces)

        assertEquals("wlan0", sorted[0].name)
        assertFalse(sorted[0].isLikelyVirtual)
    }

    @Test
    fun `eth preferred over en with same prefix and octet`() {
        val eth = NetworkInterfaceInfo("eth0", 24, "192.168.1.100", isLikelyVirtual = false)
        val en = NetworkInterfaceInfo("en0", 24, "192.168.1.100", isLikelyVirtual = false)

        val sorted = sort(listOf(en, eth))

        assertEquals("eth0", sorted[0].name)
        assertEquals("en0", sorted[1].name)
    }

    @Test
    fun `empty list returns empty`() {
        assertEquals(emptyList(), sort(emptyList()))
    }

    @Test
    fun `Windows scenario - Ethernet adapter with VMware and Hyper-V adapters`() {
        val interfaces =
            listOf(
                NetworkInterfaceInfo("vEthernet", 24, "172.28.0.1", isLikelyVirtual = true),
                NetworkInterfaceInfo("vmnet1", 24, "192.168.80.1", isLikelyVirtual = true),
                NetworkInterfaceInfo("vmnet8", 24, "192.168.200.1", isLikelyVirtual = true),
                NetworkInterfaceInfo("eth3", 24, "192.168.1.105", isLikelyVirtual = false),
            )

        val sorted = sort(interfaces)

        assertEquals("eth3", sorted[0].name)
        assertFalse(sorted[0].isLikelyVirtual)
    }
}
