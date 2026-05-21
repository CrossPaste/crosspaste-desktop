package com.crosspaste.net

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LanBypassProxySelectorTest {

    private lateinit var fakeProxy: Proxy
    private lateinit var fakeDelegate: RecordingProxySelector
    private lateinit var selector: LanBypassProxySelector

    @BeforeTest
    fun setUp() {
        fakeProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890))
        fakeDelegate = RecordingProxySelector(listOf(fakeProxy))
        selector = LanBypassProxySelector(fakeDelegate)
    }

    @AfterTest
    fun tearDown() {
        fakeDelegate.reset()
    }

    @Test
    fun `bypasses RFC1918 192_168 addresses`() {
        val proxies = selector.select(URI("http://192.168.0.111:38549/sync/telnet"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
        assertEquals(0, fakeDelegate.selectCount)
    }

    @Test
    fun `bypasses RFC1918 10_0 addresses`() {
        val proxies = selector.select(URI("http://10.20.30.40:8080/"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
    }

    @Test
    fun `bypasses RFC1918 172_16 addresses`() {
        val proxies = selector.select(URI("http://172.20.5.1:8080/"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
    }

    @Test
    fun `bypasses loopback IPv4`() {
        val proxies = selector.select(URI("http://127.0.0.1:9000/"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
    }

    @Test
    fun `bypasses link-local IPv4`() {
        val proxies = selector.select(URI("http://169.254.1.1:80/"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
    }

    @Test
    fun `bypasses CGNAT range`() {
        val proxies = selector.select(URI("http://100.96.1.1:1234/"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
    }

    @Test
    fun `bypasses IPv6 loopback`() {
        val proxies = selector.select(URI("http://[::1]:9000/"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
    }

    @Test
    fun `bypasses IPv6 link-local`() {
        val proxies = selector.select(URI("http://[fe80::1]:9000/"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
    }

    @Test
    fun `bypasses IPv6 unique-local`() {
        val proxies = selector.select(URI("http://[fd00::1]:9000/"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
    }

    @Test
    fun `bypasses mDNS local hostnames`() {
        val proxies = selector.select(URI("http://my-mac.local:9000/"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
        assertEquals(0, fakeDelegate.selectCount)
    }

    @Test
    fun `delegates public IPv4 to system selector`() {
        val proxies = selector.select(URI("http://8.8.8.8/"))
        assertEquals(listOf(fakeProxy), proxies)
        assertEquals(1, fakeDelegate.selectCount)
    }

    @Test
    fun `delegates external hostnames without DNS resolution`() {
        val proxies = selector.select(URI("https://github.com/"))
        assertEquals(listOf(fakeProxy), proxies)
        assertEquals(1, fakeDelegate.selectCount)
    }

    @Test
    fun `connectFailed delegates only for non-LAN`() {
        val sa: SocketAddress = InetSocketAddress("8.8.8.8", 443)
        selector.connectFailed(URI("https://github.com/"), sa, IOException("boom"))
        assertEquals(1, fakeDelegate.connectFailedCount)

        selector.connectFailed(URI("http://192.168.0.111:38549/"), sa, IOException("boom"))
        assertEquals(1, fakeDelegate.connectFailedCount) // unchanged
    }

    @Test
    fun `install wraps current default once`() {
        val original = ProxySelector.getDefault()
        try {
            ProxySelector.setDefault(fakeDelegate)
            LanBypassProxySelector.install()
            val first = ProxySelector.getDefault()
            assertTrue(first is LanBypassProxySelector)

            LanBypassProxySelector.install() // idempotent
            assertSame(first, ProxySelector.getDefault())
        } finally {
            ProxySelector.setDefault(original)
        }
    }

    @Test
    fun `null host is not classified as LAN`() {
        // opaque URI: getHost() returns null
        assertFalse(selector.isLanUri(URI("mailto:foo@example.com")))
    }

    private class RecordingProxySelector(
        private val response: List<Proxy>,
    ) : ProxySelector() {
        var selectCount: Int = 0
            private set
        var connectFailedCount: Int = 0
            private set

        override fun select(uri: URI?): List<Proxy> {
            selectCount++
            return response
        }

        override fun connectFailed(
            uri: URI?,
            sa: SocketAddress?,
            ioe: IOException?,
        ) {
            connectFailedCount++
        }

        fun reset() {
            selectCount = 0
            connectFailedCount = 0
        }
    }
}
