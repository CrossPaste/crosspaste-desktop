package com.crosspaste.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InterfaceAddress

class TestNetworkInterfaceService(
    private val testNetworkInterfaces: List<NetworkInterfaceInfo> = emptyList(),
    private val testPreferredInterface: NetworkInterfaceInfo? = null,
) : AbstractNetworkInterfaceService() {

    private val _networkInterfaces = MutableStateFlow(testNetworkInterfaces)
    override val networkInterfaces: StateFlow<List<NetworkInterfaceInfo>> = _networkInterfaces

    override fun getAllNetworkInterfaceInfo(): List<NetworkInterfaceInfo> = testNetworkInterfaces

    override fun getPreferredNetworkInterface(): NetworkInterfaceInfo? = testPreferredInterface

    fun testSortAddresses(addresses: List<NetworkInterfaceInfo>): List<NetworkInterfaceInfo> = sortAddresses(addresses)

    fun testProcessAddress(
        addr: InterfaceAddress,
        nicName: String,
    ): NetworkInterfaceInfo? = processAddress(addr, nicName)
}
