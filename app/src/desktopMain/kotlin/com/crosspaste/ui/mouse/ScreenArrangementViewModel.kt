package com.crosspaste.ui.mouse

import com.crosspaste.mouse.IpcEvent
import com.crosspaste.mouse.LocalScreensProvider
import com.crosspaste.mouse.MouseLayoutStore
import com.crosspaste.mouse.Position
import com.crosspaste.mouse.ScreenInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemoteDeviceInfo(
    val name: String,
    val screens: List<ScreenInfo>,
    val position: Position,
)

class ScreenArrangementViewModel(
    private val events: SharedFlow<IpcEvent>,
    private val layoutStore: MouseLayoutStore,
    private val localScreensProvider: LocalScreensProvider? = null,
) {
    /**
     * Seeded synchronously from [localScreensProvider] so the canvas can
     * render the user's monitors even before the daemon spawns (or when
     * the daemon's [IpcEvent.Initialized] fires before [observe] starts
     * collecting — `MouseDaemonManager.events` is `replay = 0`, so a
     * late subscriber never sees that event again).
     *
     * `Initialized` / `LocalScreens` events still take precedence — the
     * daemon's reading is more authoritative since it goes through the
     * native APIs the daemon will actually move the cursor across.
     */
    private val _localScreens = MutableStateFlow(localScreensProvider?.snapshot().orEmpty())
    val localScreens: StateFlow<List<ScreenInfo>> = _localScreens.asStateFlow()

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

    suspend fun observe() {
        events.collect { ev ->
            when (ev) {
                is IpcEvent.Initialized -> _localScreens.value = ev.screens
                is IpcEvent.LocalScreens -> _localScreens.value = ev.screens
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
