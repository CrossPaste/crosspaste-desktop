package com.crosspaste.mouse

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

class MouseDaemonClient(
    private val handle: DaemonHandle,
) {
    interface DaemonHandle {
        val events: SharedFlow<IpcEvent>

        suspend fun send(command: IpcCommand)

        fun close()
    }

    data class CapabilitySnapshot(
        val protocolVersion: Int = 1,
        val features: List<String> = emptyList(),
    )

    private val _capabilities = MutableStateFlow(CapabilitySnapshot())
    val capabilities: StateFlow<CapabilitySnapshot> = _capabilities.asStateFlow()

    /**
     * Transient-Stopped suppression window for the [updateLayout] fallback.
     *
     * When the daemon does not advertise `update_layout`, we simulate a
     * layout swap with `Stop` + `Start`. The daemon emits [IpcEvent.Stopped]
     * during that window — from an observer's perspective it's session
     * replacement, not a real shutdown. We hide it so downstream consumers
     * (manager state machine, UI) don't misinterpret a hot-swap as "daemon
     * stopped." The flag is set before sending `Stop` and cleared when the
     * next session's [IpcEvent.Initialized] / [IpcEvent.Ready] arrives.
     */
    private val restartInProgress = AtomicBoolean(false)

    private val _events =
        MutableSharedFlow<IpcEvent>(
            replay = 0,
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /**
     * Observable event stream. Populated by [run]; the transient [IpcEvent.Stopped]
     * emitted during [updateLayout]'s stop+start fallback is filtered here
     * (atomically with the state change that clears the restart window), so
     * late subscribers never observe a spurious shutdown.
     */
    val events: SharedFlow<IpcEvent> = _events.asSharedFlow()

    /**
     * Drives the daemon: forwards events from the handle to [events] (with
     * transient-Stopped filtering), asks for capabilities once Initialized/Ready
     * lands, and caches caps into [capabilities]. Suspends forever; cancel to stop.
     */
    suspend fun run() {
        handle.events.collect { ev ->
            // Single decision point: suppress the transient Stopped from the
            // stop+start fallback here, BEFORE downstream observers can see it.
            // Doing this at the point of entry avoids a race where a filter
            // applied downstream might be evaluated after run() has already
            // cleared restartInProgress in response to the new session's
            // Initialized event.
            if (ev is IpcEvent.Stopped && restartInProgress.get()) {
                return@collect
            }

            _events.tryEmit(ev)

            when (ev) {
                is IpcEvent.Initialized, is IpcEvent.Ready -> {
                    handle.send(IpcCommand.GetCapabilities)
                    // New session is up — any subsequent Stopped is a real shutdown.
                    restartInProgress.set(false)
                }
                is IpcEvent.Capabilities -> {
                    _capabilities.value =
                        CapabilitySnapshot(
                            protocolVersion = ev.protocolVersion,
                            features = ev.features,
                        )
                }
                else -> Unit
            }
        }
    }

    suspend fun start(
        port: Int,
        peers: List<IpcPeer>,
    ) {
        handle.send(IpcCommand.Start(port, peers))
    }

    suspend fun stop() {
        handle.send(IpcCommand.Stop)
    }

    /**
     * Applies a new layout. If the daemon advertises `update_layout`, sends it
     * natively; otherwise falls back to `Stop` + `Start` (current reality —
     * daemon ticket #9 still pending). The fallback's transient `Stopped` is
     * hidden from [events] via [restartInProgress].
     */
    suspend fun updateLayout(
        port: Int,
        peers: List<IpcPeer>,
    ) {
        if ("update_layout" in _capabilities.value.features) {
            handle.send(IpcCommand.UpdateLayout(peers))
        } else {
            restartInProgress.set(true)
            handle.send(IpcCommand.Stop)
            handle.send(IpcCommand.Start(port, peers))
            // restartInProgress cleared by run() when Initialized/Ready arrives.
        }
    }

    suspend fun getStatus() {
        handle.send(IpcCommand.GetStatus)
    }

    suspend fun enumerateLocalScreens() {
        handle.send(IpcCommand.EnumerateLocalScreens)
    }

    fun close() {
        handle.close()
    }
}
