package com.crosspaste.net

import kotlinx.coroutines.flow.StateFlow

interface NetworkInterfaceService {

    val networkInterfaces: StateFlow<List<NetworkInterfaceInfo>>

    fun getCurrentUseNetworkInterfaces(): List<NetworkInterfaceInfo> = networkInterfaces.value

    fun getAllNetworkInterfaceInfo(): List<NetworkInterfaceInfo>

    fun getPreferredNetworkInterface(): NetworkInterfaceInfo?

    fun clearProviderCache()
}
