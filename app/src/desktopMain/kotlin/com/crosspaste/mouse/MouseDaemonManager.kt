package com.crosspaste.mouse

import com.crosspaste.db.sync.SyncRuntimeInfoDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    suspend fun run() =
        coroutineScope {
            var activeClient: MouseDaemonClient? = null
            var activeClientJob: Job? = null
            var eventJob: Job? = null
            var lastPeers: List<IpcPeer>? = null
            var lastPort = -1

            combine(
                enabledFlow,
                portFlow,
                layoutStore.flow(),
                syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow(),
            ) { enabled, port, layout, syncs ->
                MergedInputs(enabled, port, MousePeerMapper.map(syncs, layout))
            }.distinctUntilChanged().collect { inputs ->
                if (!inputs.enabled) {
                    activeClient?.let {
                        runCatching { it.stop() }
                        it.close()
                    }
                    eventJob?.cancel()
                    activeClientJob?.cancel()
                    activeClient = null
                    activeClientJob = null
                    eventJob = null
                    lastPeers = null
                    lastPort = -1
                    _state.value = MouseState.Disabled
                    return@collect
                }

                if (activeClient == null) {
                    _state.value = MouseState.Starting
                    val client = clientFactory()
                    activeClient = client
                    activeClientJob = launch { client.run() }

                    // Route events into state.
                    eventJob =
                        launch {
                            client.events.collect { ev ->
                                when (ev) {
                                    is IpcEvent.PeerConnected ->
                                        _state.value =
                                            MouseState.Running(
                                                connectedPeers =
                                                    (state.value as? MouseState.Running)
                                                        ?.connectedPeers
                                                        .orEmpty() + ev.name,
                                            )
                                    is IpcEvent.PeerDisconnected ->
                                        _state.value =
                                            MouseState.Running(
                                                connectedPeers =
                                                    (state.value as? MouseState.Running)
                                                        ?.connectedPeers
                                                        .orEmpty() - ev.name,
                                            )
                                    is IpcEvent.Warning ->
                                        _state.value = MouseState.Warning(ev.code, ev.message)
                                    is IpcEvent.Error ->
                                        _state.value = MouseState.Error(ev.message)
                                    else -> Unit
                                }
                            }
                        }
                    client.start(inputs.port, inputs.peers)
                    lastPort = inputs.port
                    lastPeers = inputs.peers
                    return@collect
                }

                if (lastPort != inputs.port || lastPeers != inputs.peers) {
                    activeClient?.updateLayout(inputs.port, inputs.peers)
                    lastPort = inputs.port
                    lastPeers = inputs.peers
                }
            }
        }

    private data class MergedInputs(
        val enabled: Boolean,
        val port: Int,
        val peers: List<IpcPeer>,
    )
}
