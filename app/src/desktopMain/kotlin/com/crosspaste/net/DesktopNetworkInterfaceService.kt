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
        val config = configManager.getCurrentConfig()
        val useNetworkInterfaces =
            jsonUtils.JSON.decodeFromString<List<String>>(
                config.useNetworkInterfaces,
            )
        if (config.enableDiscovery && useNetworkInterfaces.isEmpty()) {
            autoSelectAndSavePreferredInterface()?.let { return it }
        }

        val allNetworkInterfaceInfos = getAllNetworkInterfaceInfo()

        val interfaceInfos = allNetworkInterfaceInfos.filter { it.name in useNetworkInterfaces }

        if (interfaceInfos.isEmpty() && allNetworkInterfaceInfos.isNotEmpty()) {
            autoSelectAndSavePreferredInterface()?.let { return it }
        }

        return interfaceInfos
    }

    private fun autoSelectAndSavePreferredInterface(): List<NetworkInterfaceInfo>? =
        getPreferredNetworkInterface()?.let {
            val useNetworkInterfacesJson =
                jsonUtils.JSON.encodeToString(listOf(it.name))
            configManager.updateConfig("useNetworkInterfaces", useNetworkInterfacesJson)

            listOf(it)
        }

    private fun createNetworkInterfaceInfos(useNetworkInterfacesJson: String): List<NetworkInterfaceInfo> {
        val useNetworkInterfaces =
            jsonUtils.JSON.decodeFromString<List<String>>(
                useNetworkInterfacesJson,
            )

        val allNetworkInterfaceInfos = getAllNetworkInterfaceInfo()

        return allNetworkInterfaceInfos.filter { it.name in useNetworkInterfaces }
    }
}
