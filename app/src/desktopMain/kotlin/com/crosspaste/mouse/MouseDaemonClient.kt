package com.crosspaste.mouse

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    val events: SharedFlow<IpcEvent> get() = handle.events

    /**
     * Drives the daemon: asks for capabilities once Initialized/Ready lands,
     * forwards caps into [capabilities]. Suspends forever; cancel to stop.
     */
    suspend fun run() {
        handle.events.collect { ev ->
            when (ev) {
                is IpcEvent.Initialized, is IpcEvent.Ready -> {
                    handle.send(IpcCommand.GetCapabilities)
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
     * natively; otherwise falls back to stop + start (current reality — daemon
     * ticket #9 still pending).
     */
    suspend fun updateLayout(
        port: Int,
        peers: List<IpcPeer>,
    ) {
        if ("update_layout" in _capabilities.value.features) {
            handle.send(IpcCommand.UpdateLayout(peers))
        } else {
            handle.send(IpcCommand.Stop)
            handle.send(IpcCommand.Start(port, peers))
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
