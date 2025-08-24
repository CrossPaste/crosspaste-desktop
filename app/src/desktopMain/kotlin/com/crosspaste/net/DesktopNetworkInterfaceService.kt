package com.crosspaste.net

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

class DesktopNetworkInterfaceService(
    private val configManager: DesktopConfigManager,
    networkRefreshScope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob()),
) : AbstractNetworkInterfaceService() {

    private val jsonUtils = getJsonUtils()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val networkInterfaces: StateFlow<List<NetworkInterfaceInfo>> =
        configManager.config
            .map { it.useNetworkInterfaces }
            .distinctUntilChanged()
            .mapLatest { useNetworkInterfacesJson ->
                createNetworkInterfaceInfos(useNetworkInterfacesJson)
            }.stateIn(
                scope = networkRefreshScope,
                started = SharingStarted.Eagerly,
                initialValue = initNetworkInterfaceInfos(),
            )

    private fun initNetworkInterfaceInfos(): List<NetworkInterfaceInfo> {
        if (configManager.getCurrentConfig().enableDiscovery &&
            configManager.getCurrentConfig().useNetworkInterfaces.isEmpty()
        ) {
            val preferredInterface = getPreferredNetworkInterface()
            if (preferredInterface != null) {
                val useNetworkInterfacesJson =
                    jsonUtils.JSON.encodeToString(listOf(preferredInterface.name))
                configManager.updateConfig("useNetworkInterfaces", useNetworkInterfacesJson)
            }
        }
        return createNetworkInterfaceInfos(
            configManager.getCurrentConfig().useNetworkInterfaces,
        )
    }

    private fun createNetworkInterfaceInfos(useNetworkInterfacesJson: String): List<NetworkInterfaceInfo> {
        val useNetworkInterfaces =
            jsonUtils.JSON.decodeFromString<List<String>>(
                useNetworkInterfacesJson,
            )

        val networkInterfaceInfos = getAllNetworkInterfaceInfo()

        return networkInterfaceInfos.filter { it.name in useNetworkInterfaces }
    }
}
