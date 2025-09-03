package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import io.ktor.network.sockets.InetSocketAddress

interface HostInfoFilter {

    fun filter(hostInfo: HostInfo): Boolean
}

object NoFilter : HostInfoFilter {

    override fun filter(hostInfo: HostInfo): Boolean = true

    override fun equals(other: Any?): Boolean = other == NoFilter

    override fun hashCode(): Int = 0
}

class HostInfoFilterImpl(
    val hostAddress: String,
    val networkPrefixLength: Short,
) : HostInfoFilter {

    private val selfBytes: ByteArray? by lazy { resolveIpBytes(hostAddress) }
    private val selfMaxBits: Int by lazy { (selfBytes?.size ?: 0) * 8 }

    override fun filter(hostInfo: HostInfo): Boolean {
        val otherBytes = resolveIpBytes(hostInfo.hostAddress) ?: return false
        val self = selfBytes ?: return false
        if (self.size != otherBytes.size) return false

        val maxBits = selfMaxBits
        val plen = networkPrefixLength.toInt().coerceIn(0, maxBits)

        return compareWithMask(self, otherBytes, plen)
    }

    private fun resolveIpBytes(host: String): ByteArray? =
        try {
            InetSocketAddress(host, 0).resolveAddress()
        } catch (_: Throwable) {
            null
        }

    private fun compareWithMask(
        a: ByteArray,
        b: ByteArray,
        prefixLength: Int,
    ): Boolean {
        if (a.size != b.size) return false
        val fullBytes = prefixLength / 8
        val remainBits = prefixLength % 8

        for (i in 0 until fullBytes) {
            if (a[i] != b[i]) return false
        }
        if (remainBits == 0) return true

        val mask = (0xFF shl (8 - remainBits)) and 0xFF
        val aa = a[fullBytes].toInt() and 0xFF
        val bb = b[fullBytes].toInt() and 0xFF
        return (aa and mask) == (bb and mask)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HostInfoFilterImpl) return false

        if (hostAddress != other.hostAddress) return false
        if (networkPrefixLength != other.networkPrefixLength) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hostAddress.hashCode()
        result = 31 * result + networkPrefixLength
        return result
    }
}
