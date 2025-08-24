package com.crosspaste.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TestNetworkInterfaceService(
    private val testNetworkInterfaces: List<NetworkInterfaceInfo> = emptyList(),
    private val testPreferredInterface: NetworkInterfaceInfo? = null,
) : AbstractNetworkInterfaceService() {

    private val _networkInterfaces = MutableStateFlow(testNetworkInterfaces)
    override val networkInterfaces: StateFlow<List<NetworkInterfaceInfo>> = _networkInterfaces

    override fun getAllNetworkInterfaceInfo(): List<NetworkInterfaceInfo> = testNetworkInterfaces

    override fun getPreferredNetworkInterface(): NetworkInterfaceInfo? = testPreferredInterface
}
