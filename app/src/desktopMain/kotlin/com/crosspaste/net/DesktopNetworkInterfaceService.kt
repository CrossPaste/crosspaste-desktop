package com.crosspaste.net

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

open class DesktopNetworkInterfaceService(
    private val configManager: DesktopConfigManager,
    private val networkStateMonitor: NetworkStateMonitor,
    networkRefreshScope: CoroutineScope = namedScope(ioDispatcher, "DesktopNetworkInterfaceService"),
) : AbstractNetworkInterfaceService() {

    private val jsonUtils = getJsonUtils()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override val networkInterfaces: StateFlow<List<NetworkInterfaceInfo>> =
        combine(
            // Re-resolve when the user changes the configured interfaces...
            configManager.config
                .map { it.useNetworkInterfaces }
                .distinctUntilChanged(),
            // ...or when the OS reports a real network state change. The leading
            // emit drives the initial resolution; debounce collapses flap bursts.
            networkStateMonitor.networkChanges
                .onStart { emit(Unit) }
                .debounce(NETWORK_CHANGE_DEBOUNCE),
        ) { useNetworkInterfacesJson, _ -> useNetworkInterfacesJson }
            .mapLatest { useNetworkInterfacesJson ->
                resolveNetworkInterfaceInfos(useNetworkInterfacesJson)
            }
            // Content-based: only rebuild mDNS when the interface set actually changes.
            .distinctUntilChanged()
            .stateIn(
                scope = networkRefreshScope,
                started = SharingStarted.Eagerly,
                initialValue = initNetworkInterfaceInfos(),
            )

    init {
        networkStateMonitor.start()
    }

    private fun initNetworkInterfaceInfos(): List<NetworkInterfaceInfo> =
        resolveNetworkInterfaceInfos(configManager.getCurrentConfig().useNetworkInterfaces)

    /**
     * Reads a fresh snapshot of the live interfaces and resolves them against the
     * configured selection.
     *
     * The persisted [DesktopAppConfig.useNetworkInterfaces] holds the user's
     * *preference*; the value this returns is the *active* selection actually bound
     * by discovery. They diverge only while the preferred interface is offline:
     *
     *  - Fresh install (discovery on, nothing chosen yet): auto-select a preferred
     *    interface and persist it as the bootstrap default.
     *  - Preferred interface(s) all offline but the machine is online via another
     *    interface: bind to it in-memory so discovery keeps working, but do NOT
     *    persist — the preference must survive the outage so we heal back to it once
     *    it returns (which a later network event re-resolves).
     */
    private fun resolveNetworkInterfaceInfos(useNetworkInterfacesJson: String): List<NetworkInterfaceInfo> {
        // Drop cached provider values so auto-select reflects the current network.
        clearProviderCache()

        val config = configManager.getCurrentConfig()
        val useNetworkInterfaces =
            jsonUtils.JSON.decodeFromString<List<String>>(useNetworkInterfacesJson)

        if (config.enableDiscovery && useNetworkInterfaces.isEmpty()) {
            autoSelectPreferredInterface(persist = true)?.let { return it }
        }

        val allNetworkInterfaceInfos = getAllNetworkInterfaceInfo()

        val interfaceInfos = allNetworkInterfaceInfos.filter { it.name in useNetworkInterfaces }

        if (interfaceInfos.isEmpty() && allNetworkInterfaceInfos.isNotEmpty()) {
            autoSelectPreferredInterface(persist = false)?.let { return it }
        }

        return interfaceInfos
    }

    private fun autoSelectPreferredInterface(persist: Boolean): List<NetworkInterfaceInfo>? =
        getPreferredNetworkInterface()?.let {
            if (persist) {
                val useNetworkInterfacesJson =
                    jsonUtils.JSON.encodeToString(listOf(it.name))
                configManager.updateConfig("useNetworkInterfaces", useNetworkInterfacesJson)
            }
            listOf(it)
        }

    companion object {
        private val NETWORK_CHANGE_DEBOUNCE = 500L.milliseconds
    }
}
