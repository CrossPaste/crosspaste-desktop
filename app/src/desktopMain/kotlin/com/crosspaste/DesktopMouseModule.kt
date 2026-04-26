package com.crosspaste

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.mouse.AwtLocalScreensProvider
import com.crosspaste.mouse.LocalScreensProvider
import com.crosspaste.mouse.MacosLocalScreensProvider
import com.crosspaste.mouse.MouseDaemonBinary
import com.crosspaste.mouse.MouseDaemonClient
import com.crosspaste.mouse.MouseDaemonManager
import com.crosspaste.mouse.MouseDaemonProcess
import com.crosspaste.mouse.MouseIpcProtocol
import com.crosspaste.mouse.MouseLayoutStore
import com.crosspaste.mouse.Position
import com.crosspaste.mouse.asDaemonHandle
import com.crosspaste.platform.Platform
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.mouse.ScreenArrangementViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bridges [MouseLayoutStore] to [DesktopConfigManager]-backed persistence.
 *
 * `mouseLayout` is stored as a JSON-encoded `Map<String, Position>` in
 * [com.crosspaste.config.DesktopAppConfig.mouseLayout], matching the
 * `blacklist` / `sourceExclusions` pattern: encode on write, decode on read,
 * flow through the scalar `updateConfig(key, value)` path.
 *
 * Observable [flow] is derived from `configManager.config` directly — this
 * is the single source of truth. If a save fails inside `updateConfig` and
 * the config rolls back, the derived flow automatically reflects the
 * rollback; observers never see a value that isn't persisted.
 */
class DesktopAppConfigMouseLayoutBacking(
    private val configManager: DesktopConfigManager,
) : MouseLayoutStore.Backing {

    private val logger = KotlinLogging.logger {}

    private val mapSerializer = MapSerializer(String.serializer(), Position.serializer())

    private val updateLock = Any()

    /**
     * Last JSON value we already logged a decode failure for. De-duplicates
     * warnings across the many `decode()` calls that happen while a corrupt
     * config value keeps sitting in [DesktopConfigManager.config] — we only
     * want one warning per distinct bad payload.
     */
    @Volatile
    private var lastWarnedJson: String? = null

    override fun snapshot(): Map<String, Position> = decode(configManager.config.value.mouseLayout)

    override fun update(updater: (Map<String, Position>) -> Map<String, Position>) {
        synchronized(updateLock) {
            val current = decode(configManager.config.value.mouseLayout)
            val next = updater(current)
            configManager.updateConfig(
                "mouseLayout",
                MouseIpcProtocol.json.encodeToString(mapSerializer, next),
            )
        }
    }

    override fun flow(): Flow<Map<String, Position>> =
        configManager.config
            .map { decode(it.mouseLayout) }
            .distinctUntilChanged()

    private fun decode(json: String): Map<String, Position> =
        runCatching { MouseIpcProtocol.json.decodeFromString(mapSerializer, json) }
            .getOrElse { e ->
                if (lastWarnedJson != json) {
                    lastWarnedJson = json
                    logger.warn(e) {
                        "Failed to decode mouseLayout JSON; falling back to empty layout. " +
                            "Offending value (truncated): ${json.take(200)}"
                    }
                }
                emptyMap()
            }
}

fun desktopMouseModule(): Module =
    module {
        single<LocalScreensProvider> {
            val platform = get<Platform>()
            if (platform.isMacos()) MacosLocalScreensProvider() else AwtLocalScreensProvider()
        }
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
                // Source the paired-device list from SyncManager's already-shared
                // StateFlow rather than the raw DAO flow. The DAO flow is fine to
                // observe in isolation but conceptually mouse cares about "business
                // peers managed by SyncManager", not about raw table rows — and
                // SyncManager's StateFlow is the canonical multi-cast view.
                syncRuntimeInfosFlow = get<SyncManager>().realTimeSyncRuntimeInfos,
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
                localScreensProvider = get(),
            )
        }
    }
