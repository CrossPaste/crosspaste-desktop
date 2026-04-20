package com.crosspaste

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.mouse.MouseDaemonBinary
import com.crosspaste.mouse.MouseDaemonClient
import com.crosspaste.mouse.MouseDaemonManager
import com.crosspaste.mouse.MouseDaemonProcess
import com.crosspaste.mouse.MouseLayoutStore
import com.crosspaste.mouse.Position
import com.crosspaste.mouse.asDaemonHandle
import com.crosspaste.ui.mouse.ScreenArrangementViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bridges [MouseLayoutStore] to [DesktopConfigManager]-backed persistence.
 *
 * Reads are served off the current config snapshot. Writes go through
 * [DesktopConfigManager.updateConfig] (the typed-updater overload) so the
 * layout persists to `appConfig.json` and the config StateFlow stays in
 * sync. The store's own [flow] is the authoritative observable surface —
 * it's updated in lockstep with `set()` so observers see new layouts
 * immediately, independent of any config-save latency.
 */
class DesktopAppConfigMouseLayoutBacking(
    private val configManager: DesktopConfigManager,
) : MouseLayoutStore.Backing {

    private val _flow: MutableStateFlow<Map<String, Position>> =
        MutableStateFlow(configManager.config.value.mouseLayout)

    override fun snapshot(): Map<String, Position> = configManager.config.value.mouseLayout

    override fun set(newMap: Map<String, Position>) {
        configManager.updateConfig { it.copy(mouseLayout = newMap) }
        _flow.value = newMap
    }

    override fun flow(): MutableStateFlow<Map<String, Position>> = _flow
}

fun desktopMouseModule(): Module =
    module {
        single<MouseLayoutStore> {
            MouseLayoutStore(DesktopAppConfigMouseLayoutBacking(get<DesktopConfigManager>()))
        }
        single<MouseDaemonManager> {
            val configManager = get<DesktopConfigManager>()
            MouseDaemonManager(
                enabledFlow =
                    configManager.config
                        .map { it.mouseEnabled }
                        .distinctUntilChanged(),
                portFlow =
                    configManager.config
                        .map { it.mouseListenPort }
                        .distinctUntilChanged(),
                layoutStore = get(),
                syncRuntimeInfoDao = get(),
                clientFactory = {
                    val binary =
                        MouseDaemonBinary.resolve()
                            ?: throw IllegalStateException("crosspaste-mouse binary not found")
                    MouseDaemonClient(MouseDaemonProcess.spawn(binary).asDaemonHandle())
                },
            )
        }
        factory<ScreenArrangementViewModel> {
            ScreenArrangementViewModel(
                events = get<MouseDaemonManager>().events,
                layoutStore = get(),
            )
        }
    }
