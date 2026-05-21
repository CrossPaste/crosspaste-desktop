package com.crosspaste.net

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Forces DIRECT connection for LAN addresses while delegating all
 * other URIs to the original system proxy selector.
 *
 * Why this exists: JBR mirrors macOS / Windows system proxy settings
 * into JVM system properties (`http.proxyHost` etc.) at startup. Java's
 * `http.nonProxyHosts` uses shell-style wildcards and does NOT understand
 * CIDR entries (e.g. `192.168.0.0/16`). When macOS configures bypass
 * rules with CIDR, the JVM treats them as literal strings, every LAN
 * address falls through to the proxy, and peer-to-peer sync requests
 * are routed to a proxy that cannot reach the LAN peer — so they hang
 * until the per-request timeout fires.
 *
 * This selector explicitly classifies LAN traffic (loopback, link-local,
 * RFC1918 site-local, CGNAT, IPv6 ULA, and `.local` mDNS names) and
 * short-circuits it to DIRECT, leaving outbound internet traffic on the
 * user's configured proxy.
 */
class LanBypassProxySelector(
    private val delegate: ProxySelector,
) : ProxySelector() {

    override fun select(uri: URI): List<Proxy> {
        if (isLanUri(uri)) {
            return DIRECT
        }
        return delegate.select(uri)
    }

    override fun connectFailed(
        uri: URI,
        sa: SocketAddress,
        ioe: IOException,
    ) {
        if (!isLanUri(uri)) {
            delegate.connectFailed(uri, sa, ioe)
        }
    }

    internal fun isLanUri(uri: URI): Boolean {
        val rawHost = uri.host ?: return false
        // URI.getHost() returns IPv6 literals without brackets, so just trim defensively.
        val host = rawHost.trim('[', ']')

        if (host.endsWith(".local", ignoreCase = true)) return true

        if (!looksLikeIpLiteral(host)) return false

        return runCatching {
            // For IP literals (matched above) getByName never triggers DNS resolution.
            // Strip the IPv6 zone id ("fe80::1%en0") before parsing — addresses with
            // a zone are always link-local.
            val parseable = host.substringBefore('%')
            val addr = InetAddress.getByName(parseable)
            addr.isLoopbackAddress ||
                addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress ||
                addr.isAnyLocalAddress ||
                isCgnatV4(addr) ||
                isUniqueLocalV6(addr)
        }.getOrElse {
            logger.debug(it) { "LanBypassProxySelector: failed to classify $host" }
            false
        }
    }

    private fun isCgnatV4(addr: InetAddress): Boolean {
        val bytes = addr.address
        if (bytes.size != 4) return false
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        return first == 100 && second in 64..127 // 100.64.0.0/10
    }

    private fun isUniqueLocalV6(addr: InetAddress): Boolean {
        // Java's isSiteLocalAddress only recognises the deprecated fec0::/10 prefix,
        // not modern ULA (fc00::/7). Treat both fc00::/8 and fd00::/8 as LAN.
        val bytes = addr.address
        if (bytes.size != 16) return false
        val first = bytes[0].toInt() and 0xFF
        return first == 0xFC || first == 0xFD
    }

    private fun looksLikeIpLiteral(host: String): Boolean {
        if (host.isEmpty()) return false
        if (host.contains(':')) {
            // IPv6 literal (may carry a zone id like %en0)
            return host.all {
                it.isDigit() ||
                    it in 'a'..'f' ||
                    it in 'A'..'F' ||
                    it == ':' ||
                    it == '.' ||
                    it == '%' ||
                    it.isLetter() ||
                    it.isDigit()
            }
        }
        if (!host.contains('.')) return false
        return host.all { it.isDigit() || it == '.' }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        private val DIRECT = listOf(Proxy.NO_PROXY)

        /**
         * Wrap the current default ProxySelector with LAN bypass. Idempotent:
         * calling more than once leaves a single wrapper in place.
         */
        fun install() {
            val current = getDefault() ?: return
            if (current is LanBypassProxySelector) return
            setDefault(LanBypassProxySelector(current))
            logger.info { "LanBypassProxySelector installed (delegate=${current::class.java.name})" }
        }
    }
}
