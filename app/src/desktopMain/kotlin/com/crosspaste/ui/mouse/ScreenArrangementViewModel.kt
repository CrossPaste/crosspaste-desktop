package com.crosspaste.ui.mouse

import com.crosspaste.mouse.IpcEvent
import com.crosspaste.mouse.LocalScreensProvider
import com.crosspaste.mouse.MouseLayoutStore
import com.crosspaste.mouse.Position
import com.crosspaste.mouse.ScreenInfo
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RemoteDeviceInfo(
    val name: String,
    val screens: List<ScreenInfo>,
    val position: Position,
)

class ScreenArrangementViewModel(
    private val events: SharedFlow<IpcEvent>,
    private val layoutStore: MouseLayoutStore,
    private val localScreensProvider: LocalScreensProvider? = null,
    private val seedDispatcher: CoroutineDispatcher = ioDispatcher,
) {
    /**
     * Seeded lazily from [localScreensProvider] inside [observe] (off the UI
     * thread) so the canvas can render the user's monitors even before the
     * daemon spawns — or when the daemon's [IpcEvent.Initialized] fires before
     * [observe] starts collecting (`MouseDaemonManager.events` is `replay = 0`,
     * so a late subscriber never sees that event again).
     *
     * The seed is NOT run in the constructor: [localScreensProvider.snapshot]
     * does blocking native I/O on macOS (reads each monitor's wallpaper and
     * re-encodes it to a PNG), and the view model is a Koin `factory` resolved
     * during composition — running it synchronously froze the UI thread on
     * every window open. It now loads on [ioDispatcher] and only fills the
     * still-empty list, so any authoritative `Initialized` / `LocalScreens`
     * event that arrives first keeps precedence.
     */
    private val _localScreens = MutableStateFlow<List<ScreenInfo>>(emptyList())
    val localScreens: StateFlow<List<ScreenInfo>> = _localScreens.asStateFlow()

    private val screensLock = Any()

    /**
     * Local platform snapshot (geometry + enriched `name` / `wallpaperPath`).
     * The daemon's IPC screens carry geometry only — `name` / `wallpaperPath`
     * are `@Transient` on [ScreenInfo] and never cross the IPC boundary — so we
     * keep this snapshot to re-attach the enrichment by display id whenever the
     * daemon reports screens. Guarded by [screensLock].
     */
    private var seedScreens: List<ScreenInfo> = emptyList()

    /**
     * Latest daemon-reported local screens (authoritative geometry), or null
     * before the daemon initializes. Guarded by [screensLock].
     */
    private var daemonScreens: List<ScreenInfo>? = null

    private val _remoteDevices = MutableStateFlow<Map<String, RemoteDeviceInfo>>(emptyMap())
    val remoteDevices: StateFlow<Map<String, RemoteDeviceInfo>> = _remoteDevices.asStateFlow()

    /** Test-only: inject a remote device without going through the event stream. */
    internal fun seedRemote(
        deviceId: String,
        name: String,
        screens: List<ScreenInfo>,
    ) {
        val existing = _remoteDevices.value[deviceId]
        val position = existing?.position ?: layoutStore.get(deviceId) ?: Position(0, 0)
        _remoteDevices.value =
            _remoteDevices.value + (deviceId to RemoteDeviceInfo(name, screens, position))
    }

    /**
     * Re-attach local `name` / `wallpaperPath` to a daemon-reported screen by
     * matching display id. Falls back to the daemon screen unchanged when no
     * local match exists (disjoint ids, or the AWT fallback that carries no
     * names) — enrichment is best-effort and never degrades geometry.
     *
     * Assumes the daemon emits the same display id space as
     * [com.crosspaste.mouse.MacosLocalScreensProvider] (CGDirectDisplayID on
     * macOS); if they ever diverge, the match simply misses and we render the
     * daemon screen as-is, same as before this enrichment existed.
     */
    private fun ScreenInfo.withLocalEnrichment(): ScreenInfo {
        val local = seedScreens.firstOrNull { it.id == id } ?: return this
        return copy(
            name = name ?: local.name,
            wallpaperPath = wallpaperPath ?: local.wallpaperPath,
        )
    }

    /**
     * Recompute and publish the canvas's local-screen list under [screensLock]:
     * daemon geometry enriched with local names/wallpaper once the daemon has
     * reported, otherwise the raw local snapshot as a pre-daemon seed.
     */
    private fun updateScreens(mutate: () -> Unit) {
        synchronized(screensLock) {
            mutate()
            _localScreens.value =
                daemonScreens?.map { it.withLocalEnrichment() } ?: seedScreens
        }
    }

    suspend fun observe(): Unit =
        coroutineScope {
            // Seed the canvas off the UI thread; snapshot() does blocking native
            // I/O (wallpaper PNG encode per monitor) on macOS. Publishing merges
            // with any daemon screens already reported, so the enrichment is
            // re-attached even if Initialized arrived first.
            launch(seedDispatcher) {
                val seed = localScreensProvider?.snapshot().orEmpty()
                if (seed.isNotEmpty()) {
                    updateScreens { seedScreens = seed }
                }
            }
            events.collect { ev ->
                when (ev) {
                    is IpcEvent.Initialized -> updateScreens { daemonScreens = ev.screens }
                    is IpcEvent.LocalScreens -> updateScreens { daemonScreens = ev.screens }
                    is IpcEvent.PeerScreensLearned -> {
                        val existing = _remoteDevices.value[ev.deviceId]
                        _remoteDevices.value =
                            _remoteDevices.value + (
                                ev.deviceId to
                                    RemoteDeviceInfo(
                                        name = existing?.name ?: ev.deviceId,
                                        screens = ev.screens,
                                        position =
                                            existing?.position
                                                ?: layoutStore.get(ev.deviceId)
                                                ?: Position(0, 0),
                                    )
                            )
                    }
                    is IpcEvent.PeerConnected -> {
                        val existing = _remoteDevices.value[ev.deviceId]
                        if (existing != null && existing.name != ev.name) {
                            _remoteDevices.value =
                                _remoteDevices.value + (ev.deviceId to existing.copy(name = ev.name))
                        }
                    }
                    else -> Unit
                }
            }
        }

    fun onDragDevice(
        deviceId: String,
        dx: Int,
        dy: Int,
    ) {
        val existing = _remoteDevices.value[deviceId] ?: return
        val next =
            existing.copy(
                position = Position(existing.position.x + dx, existing.position.y + dy),
            )
        _remoteDevices.value = _remoteDevices.value + (deviceId to next)
    }

    fun onDragEnd(deviceId: String) {
        val dev = _remoteDevices.value[deviceId] ?: return
        layoutStore.upsert(deviceId, dev.position)
    }
}
