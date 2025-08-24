package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import java.net.InetAddress
import java.nio.ByteBuffer

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

    override fun filter(hostInfo: HostInfo): Boolean =
        networkPrefixLength == hostInfo.networkPrefixLength &&
            hostPreFixMatch(hostAddress, hostInfo.hostAddress, networkPrefixLength)

    private fun hostPreFixMatch(
        host1: String,
        host2: String,
        prefixLength: Short,
    ): Boolean {
        // Convert host IP addresses to InetAddress objects
        val inetAddress1 = InetAddress.getByName(host1)
        val inetAddress2 = InetAddress.getByName(host2)

        // Convert InetAddress to byte arrays
        val bytes1 = inetAddress1.address
        val bytes2 = inetAddress2.address

        // Calculate subnet mask from prefixLength, ensuring it doesn't exceed 32 bits
        val maskLength = if (prefixLength > 32) 32 else prefixLength.toInt()
        val mask = -0x1 shl (32 - maskLength)

        // Convert byte arrays to integers and apply the subnet mask
        val addr1 = ByteBuffer.wrap(bytes1).int and mask
        val addr2 = ByteBuffer.wrap(bytes2).int and mask

        // Compare the masked addresses to check if they are in the same subnet
        return addr1 == addr2
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
