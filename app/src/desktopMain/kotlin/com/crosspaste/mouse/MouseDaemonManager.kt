package com.crosspaste.mouse

import com.crosspaste.db.sync.SyncRuntimeInfoDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

sealed interface MouseState {
    object Disabled : MouseState

    object Starting : MouseState

    data class Running(
        val connectedPeers: List<String>,
    ) : MouseState

    data class Warning(
        val code: String,
        val message: String,
    ) : MouseState

    data class Error(
        val message: String,
    ) : MouseState
}

/**
 * Runtime orchestrator for the crosspaste-mouse daemon.
 *
 * Observes the `mouseEnabled` flag, `mouseListenPort`, the layout store, and the
 * paired-device list. When enabled, spawns a daemon client (via [clientFactory]),
 * sends `Start { port, peers }`, and translates further input changes into
 * `updateLayout` calls. When disabled, stops the client and tears it down.
 *
 * `run()` is expected to be launched once at app startup and live for the app
 * lifetime — cancel the launched job to shut the manager down.
 */
class MouseDaemonManager(
    private val enabledFlow: Flow<Boolean>,
    private val portFlow: Flow<Int>,
    private val layoutStore: MouseLayoutStore,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val clientFactory: () -> MouseDaemonClient,
) {
    private val _state = MutableStateFlow<MouseState>(MouseState.Disabled)
    val state: StateFlow<MouseState> = _state.asStateFlow()

    /**
     * Aggregated event stream multiplexed across the manager's active clients.
     * UI components (e.g. [com.crosspaste.ui.mouse.ScreenArrangementViewModel])
     * can subscribe here to receive `IpcEvent`s without having to plumb a
     * specific client session through DI — the manager always forwards events
     * from whichever client is currently active.
     */
    private val _events =
        MutableSharedFlow<IpcEvent>(
            replay = 0,
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<IpcEvent> = _events.asSharedFlow()

    /** Snapshot of an active daemon session (bundle of client + coroutines + last inputs). */
    private data class Session(
        val client: MouseDaemonClient,
        val runJob: Job,
        val eventJob: Job,
        val port: Int,
        val peers: List<IpcPeer>,
    )

    suspend fun run() =
        coroutineScope {
            var session: Session? = null
            combine(
                enabledFlow,
                portFlow,
                layoutStore.flow(),
                syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow(),
            ) { enabled, port, layout, syncs ->
                MergedInputs(enabled, port, MousePeerMapper.map(syncs, layout))
            }.distinctUntilChanged()
                .collect { inputs ->
                    session =
                        when {
                            !inputs.enabled -> teardownClient(session)
                            session == null -> spawnClient(inputs)
                            else -> updateClientLayout(session!!, inputs)
                        }
                }
        }

    /** Stop, close, and cancel the current session's coroutines; reset state. */
    private suspend fun teardownClient(current: Session?): Session? {
        current?.let {
            runCatching { it.client.stop() }
            it.client.close()
            it.eventJob.cancel()
            it.runJob.cancel()
        }
        _state.value = MouseState.Disabled
        return null
    }

    /** Create a new client, wire up event forwarding, send the initial `Start`. */
    private suspend fun CoroutineScope.spawnClient(inputs: MergedInputs): Session {
        _state.value = MouseState.Starting
        val client = clientFactory()
        val runJob = launch { client.run() }
        val eventJob = launch { forwardClientEvents(client.events) }
        client.start(inputs.port, inputs.peers)
        return Session(client, runJob, eventJob, inputs.port, inputs.peers)
    }

    /** Push a layout/port diff down to the existing session via `updateLayout`. */
    private suspend fun updateClientLayout(
        current: Session,
        inputs: MergedInputs,
    ): Session {
        if (current.port == inputs.port && current.peers == inputs.peers) {
            return current
        }
        current.client.updateLayout(inputs.port, inputs.peers)
        return current.copy(port = inputs.port, peers = inputs.peers)
    }

    /** Re-broadcast every event for UI subscribers and translate into [MouseState] transitions. */
    private suspend fun forwardClientEvents(source: SharedFlow<IpcEvent>) {
        source.collect { ev ->
            _events.tryEmit(ev)
            when (ev) {
                // Initialized/Ready means the daemon successfully spun up a
                // session. Treat it as "running with no connected peers
                // yet" — also clears any prior Error/Warning so a transient
                // failure (e.g. log spillover briefly mis-parsed before
                // we filtered it out) doesn't get stuck on screen.
                is IpcEvent.Initialized, is IpcEvent.Ready -> {
                    if (state.value !is MouseState.Running) {
                        _state.value = MouseState.Running(emptyList())
                    }
                }
                is IpcEvent.PeerConnected -> {
                    val prev = (state.value as? MouseState.Running)?.connectedPeers.orEmpty()
                    _state.value = MouseState.Running(prev + ev.name)
                }
                is IpcEvent.PeerDisconnected -> {
                    val prev = (state.value as? MouseState.Running)?.connectedPeers.orEmpty()
                    _state.value = MouseState.Running(prev - ev.name)
                }
                is IpcEvent.Warning -> _state.value = MouseState.Warning(ev.code, ev.message)
                is IpcEvent.Error -> _state.value = MouseState.Error(ev.message)
                else -> Unit
            }
        }
    }

    private data class MergedInputs(
        val enabled: Boolean,
        val port: Int,
        val peers: List<IpcPeer>,
    )
}
