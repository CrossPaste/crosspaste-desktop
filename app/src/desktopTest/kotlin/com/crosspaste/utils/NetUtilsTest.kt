package com.crosspaste.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetUtilsTest {

    @Test
    fun normalizeRoutableIpv4_acceptsRoutableIpv4AndUnwrapsMappedIpv6() {
        assertEquals("100.64.0.1", NetUtils.normalizeRoutableIpv4("100.64.0.1"))
        assertEquals("192.168.1.10", NetUtils.normalizeRoutableIpv4(" 192.168.1.10 "))
        assertEquals("100.64.0.1", NetUtils.normalizeRoutableIpv4("::ffff:100.64.0.1"))
        assertEquals("100.64.0.1", NetUtils.normalizeRoutableIpv4("[::FFFF:100.64.0.1]"))
    }

    @Test
    fun normalizeRoutableIpv4_rejectsLoopbackUnspecifiedIpv6AndInvalid() {
        assertNull(NetUtils.normalizeRoutableIpv4(null))
        assertNull(NetUtils.normalizeRoutableIpv4(""))
        assertNull(NetUtils.normalizeRoutableIpv4("127.0.0.1"))
        assertNull(NetUtils.normalizeRoutableIpv4("127.0.1.1"))
        assertNull(NetUtils.normalizeRoutableIpv4("::ffff:127.0.0.1"))
        assertNull(NetUtils.normalizeRoutableIpv4("0.0.0.0"))
        assertNull(NetUtils.normalizeRoutableIpv4("::1"))
        assertNull(NetUtils.normalizeRoutableIpv4("fd7a:115c:a1e0::1"))
        assertNull(NetUtils.normalizeRoutableIpv4("localhost"))
        assertNull(NetUtils.normalizeRoutableIpv4("999.1.2.3"))
    }
}
