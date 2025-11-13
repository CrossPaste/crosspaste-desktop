package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.net.HostInfoFilter.Companion.createHostInfoFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HostInfoFilterTest {

    @Test
    fun testNoFilterAlwaysReturnsTrue() {
        val hostInfo1 = HostInfo(networkPrefixLength = 24, hostAddress = "192.168.1.1")
        val hostInfo2 = HostInfo(networkPrefixLength = 16, hostAddress = "10.0.0.1")
        val hostInfo3 = HostInfo(networkPrefixLength = 8, hostAddress = "invalid-ip")

        assertTrue(NoFilter.filter(hostInfo1))
        assertTrue(NoFilter.filter(hostInfo2))
        assertTrue(NoFilter.filter(hostInfo3))
    }

    @Test
    fun testNoFilterEquals() {
        assertSame(NoFilter, NoFilter)
        assertFalse(NoFilter.equals(null))
        assertFalse(NoFilter.equals("not-a-filter"))
        assertFalse(NoFilter.equals(createHostInfoFilter("192.168.1.1", 24)))
    }

    @Test
    fun testNoFilterHashCode() {
        assertEquals(NoFilter.hashCode(), 0)
        assertEquals(NoFilter.hashCode(), NoFilter.hashCode())
    }

    @Test
    fun testIPv4SameSubnetFiltering() {
        val filter = createHostInfoFilter("192.168.1.100", 24)

        assertTrue(filter.filter(HostInfo(24, "192.168.1.1")))
        assertTrue(filter.filter(HostInfo(24, "192.168.1.50")))
        assertTrue(filter.filter(HostInfo(24, "192.168.1.255")))
        assertTrue(filter.filter(HostInfo(24, "192.168.1.100")))
    }

    @Test
    fun testIPv4DifferentSubnetFiltering() {
        val filter = createHostInfoFilter("192.168.1.100", 24)

        assertFalse(filter.filter(HostInfo(24, "192.168.2.1")))
        assertFalse(filter.filter(HostInfo(24, "10.0.0.1")))
        assertFalse(filter.filter(HostInfo(24, "172.16.0.1")))
        assertFalse(filter.filter(HostInfo(24, "1.1.1.1")))
    }

    @Test
    fun testIPv4SmallerSubnetFiltering() {
        val filter = createHostInfoFilter("192.168.1.100", 16)

        assertTrue(filter.filter(HostInfo(16, "192.168.1.1")))
        assertTrue(filter.filter(HostInfo(16, "192.168.255.255")))
        assertTrue(filter.filter(HostInfo(16, "192.168.0.1")))

        assertFalse(filter.filter(HostInfo(16, "192.167.1.1")))
        assertFalse(filter.filter(HostInfo(16, "192.169.1.1")))
        assertFalse(filter.filter(HostInfo(16, "10.0.1.1")))
    }

    @Test
    fun testIPv4LargerSubnetFiltering() {
        val filter = createHostInfoFilter("192.168.1.100", 30)

        assertTrue(filter.filter(HostInfo(30, "192.168.1.100")))
        assertTrue(filter.filter(HostInfo(30, "192.168.1.101")))
        assertTrue(filter.filter(HostInfo(30, "192.168.1.102")))
        assertTrue(filter.filter(HostInfo(30, "192.168.1.103")))

        assertFalse(filter.filter(HostInfo(30, "192.168.1.104")))
        assertFalse(filter.filter(HostInfo(30, "192.168.1.99")))
        assertFalse(filter.filter(HostInfo(30, "192.168.1.1")))
    }

    @Test
    fun testIPv6SameSubnetFiltering() {
        val filter = createHostInfoFilter("2001:db8:85a3::8a2e:370:7334", 64)

        assertTrue(filter.filter(HostInfo(64, "2001:db8:85a3::1")))
        assertTrue(filter.filter(HostInfo(64, "2001:db8:85a3::ffff")))
        assertTrue(filter.filter(HostInfo(64, "2001:db8:85a3:0:1234:5678:9abc:def0")))
    }

    @Test
    fun testIPv6DifferentSubnetFiltering() {
        val filter = createHostInfoFilter("2001:db8:85a3::8a2e:370:7334", 64)

        assertFalse(filter.filter(HostInfo(64, "2001:db8:85a4::1")))
        assertFalse(filter.filter(HostInfo(64, "2001:db8:85a2::1")))
        assertFalse(filter.filter(HostInfo(64, "fe80::1")))
        assertFalse(filter.filter(HostInfo(64, "::1")))
    }

    @Test
    fun testIPv6MixedWithIPv4() {
        val ipv4Filter = createHostInfoFilter("192.168.1.100", 24)
        val ipv6Filter = createHostInfoFilter("2001:db8:85a3::1", 64)

        assertFalse(ipv4Filter.filter(HostInfo(64, "2001:db8:85a3::1")))
        assertFalse(ipv6Filter.filter(HostInfo(24, "192.168.1.1")))
    }

    @Test
    fun testInvalidIPAddressHandling() {
        val filter = createHostInfoFilter("192.168.1.100", 24)

        assertFalse(filter.filter(HostInfo(24, "invalid-ip")))
        assertFalse(filter.filter(HostInfo(24, "999.999.999.999")))
        assertFalse(filter.filter(HostInfo(24, "192.168.1")))
        assertFalse(filter.filter(HostInfo(24, "192.168.1.1.1")))
        assertFalse(filter.filter(HostInfo(24, "")))
    }

    @Test
    fun testInvalidSelfIPAddress() {
        val filter = createHostInfoFilter("invalid-ip", 24)

        assertFalse(filter.filter(HostInfo(24, "192.168.1.1")))
        assertFalse(filter.filter(HostInfo(24, "invalid-ip")))
    }

    @Test
    fun testBoundaryConditionsPrefixLength0() {
        val filter = createHostInfoFilter("192.168.1.100", 0)

        assertTrue(filter.filter(HostInfo(0, "192.168.1.1")))
        assertTrue(filter.filter(HostInfo(0, "10.0.0.1")))
        assertTrue(filter.filter(HostInfo(0, "172.16.0.1")))
        assertTrue(filter.filter(HostInfo(0, "1.1.1.1")))
    }

    @Test
    fun testBoundaryConditionsPrefixLength32() {
        val filter = createHostInfoFilter("192.168.1.100", 32)

        assertTrue(filter.filter(HostInfo(32, "192.168.1.100")))

        assertFalse(filter.filter(HostInfo(32, "192.168.1.101")))
        assertFalse(filter.filter(HostInfo(32, "192.168.1.99")))
        assertFalse(filter.filter(HostInfo(32, "192.168.1.1")))
    }

    @Test
    fun testBoundaryConditionsPrefixLength128IPv6() {
        val filter = createHostInfoFilter("2001:db8:85a3::8a2e:370:7334", 128)

        assertTrue(filter.filter(HostInfo(128, "2001:db8:85a3::8a2e:370:7334")))

        assertFalse(filter.filter(HostInfo(128, "2001:db8:85a3::8a2e:370:7335")))
        assertFalse(filter.filter(HostInfo(128, "2001:db8:85a3::1")))
    }

    @Test
    fun testNegativePrefixLength() {
        val filter = createHostInfoFilter("192.168.1.100", -5)

        assertTrue(filter.filter(HostInfo(-5, "192.168.1.1")))
        assertTrue(filter.filter(HostInfo(-5, "10.0.0.1")))
    }

    @Test
    fun testExcessivePrefixLength() {
        val filter = createHostInfoFilter("192.168.1.100", 50)

        assertTrue(filter.filter(HostInfo(50, "192.168.1.100")))
        assertFalse(filter.filter(HostInfo(50, "192.168.1.101")))
    }

    @Test
    fun testcreateHostInfoFilterEquals() {
        val filter1 = createHostInfoFilter("192.168.1.100", 24)
        val filter2 = createHostInfoFilter("192.168.1.100", 24)
        val filter3 = createHostInfoFilter("192.168.1.101", 24)
        val filter4 = createHostInfoFilter("192.168.1.100", 16)

        assertEquals(filter1, filter1)
        assertEquals(filter1, filter2)
        assertEquals(filter2, filter1)

        assertNotEquals(filter1, filter3)
        assertNotEquals(filter1, filter4)
        assertFalse(filter1.equals(null))
        assertFalse(filter1.equals("not-a-filter"))
        assertFalse(filter1.equals(NoFilter))
    }

    @Test
    fun testcreateHostInfoFilterHashCode() {
        val filter1 = createHostInfoFilter("192.168.1.100", 24)
        val filter2 = createHostInfoFilter("192.168.1.100", 24)
        val filter3 = createHostInfoFilter("192.168.1.101", 24)

        assertEquals(filter1.hashCode(), filter2.hashCode())
        assertTrue(filter1.hashCode() != filter3.hashCode())
    }
}
