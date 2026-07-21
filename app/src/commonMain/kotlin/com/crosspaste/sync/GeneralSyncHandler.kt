package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfo.Companion.hostInfoListEqual
import com.crosspaste.db.sync.SyncState
import com.crosspaste.net.VersionRelation
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class GeneralSyncHandler(
    syncRuntimeInfo: SyncRuntimeInfo,
    private val emitEvent: suspend (SyncEvent) -> Unit,
    private val syncHandlerScope: CoroutineScope = namedScope(ioDispatcher, "GeneralSyncHandler"),
) : SyncHandler {

    private val _syncRuntimeInfo: MutableStateFlow<SyncRuntimeInfo> = MutableStateFlow(syncRuntimeInfo)

    override val syncRuntimeInfoFlow: StateFlow<SyncRuntimeInfo> = _syncRuntimeInfo

    private val _versionRelation: MutableStateFlow<VersionRelation> = MutableStateFlow(VersionRelation.EQUAL_TO)

    override var versionRelation: StateFlow<VersionRelation> = _versionRelation

    // @VisibleForTesting
    internal val syncPollingManager = SyncPollingManager(syncHandlerScope)

    private val job: Job

    init {
        syncHandlerScope.launch {
            syncRuntimeInfoFlow
                .scan(Pair<SyncRuntimeInfo?, SyncRuntimeInfo?>(null, null)) { prev, current ->
                    Pair(prev.second, current)
                }.collect { (previous, current) ->
                    when {
                        current != null -> {
                            if (previous == null) {
                                handleFirstValue(current)
                            } else {
                                handleValueChange(previous, current)
                            }
                        }
                    }
                }
        }

        job =
            syncPollingManager.startPollingResolve {
                emitEvent(SyncEvent.Resolve(currentSyncRuntimeInfo, createPollingCallback()))
            }
    }

    private fun createCallback(onComplete: () -> Unit = {}): ResolveCallback =
        ResolveCallback(
            updateVersionRelation = ::updateVersionRelation,
            markPollFailure = {},
            onComplete = onComplete,
        )

    // @VisibleForTesting
    internal fun createPollingCallback(): ResolveCallback =
        ResolveCallback(
            updateVersionRelation = ::updateVersionRelation,
            markPollFailure = { syncPollingManager.fail() },
            onComplete = {},
        )

    override fun updateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo) {
        _syncRuntimeInfo.value = syncRuntimeInfo
    }

    private fun updateVersionRelation(relation: VersionRelation) {
        _versionRelation.value = relation
    }

    private suspend fun handleFirstValue(syncRuntimeInfo: SyncRuntimeInfo) {
        // The resolver handles all states — just emit a unified Resolve event
        emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createCallback()))
    }

    private suspend fun handleValueChange(
        previous: SyncRuntimeInfo,
        current: SyncRuntimeInfo,
    ) {
        if (current.port != previous.port) {
            emitEvent(SyncEvent.Resolve(current, createCallback()))
            return
        }

        // A changed connect address only warrants an immediate re-resolve when the
        // device is currently CONNECTED (e.g. mDNS advertised a new address for a
        // live peer). For CONNECTING / DISCONNECTED / etc. we must NOT short-circuit
        // here — that would re-emit Resolve on every DB write and reproduce the #4499 /
        // #4500 tight loop (a discover -> authenticate -> fail pass thrashes the address
        // CONNECTING@new <-> DISCONNECTED@stale). Backoff is no longer applied in
        // handleValueChange at all: it is driven solely by the polling loop's
        // markPollFailure (see createPollingCallback), so external/discovery DB writes
        // can never starve polling (discovery-driven-fast-reconnect, fix one).
        if (current.connectState == SyncState.CONNECTED &&
            previous.connectHostAddress != null &&
            current.connectHostAddress != null &&
            previous.connectHostAddress != current.connectHostAddress
        ) {
            emitEvent(SyncEvent.Resolve(current, createCallback()))
            return
        }

        if (current.connectState != previous.connectState) {
            when (current.connectState) {
                SyncState.DISCONNECTED -> {
                    emitEvent(SyncEvent.RefreshSyncInfo(current.appInstanceId, current.hostInfoList))
                    // Only attempt immediate reconnection when transitioning from CONNECTED.
                    // For CONNECTING -> DISCONNECTED (a failed connection attempt),
                    // let polling handle retry with backoff to avoid tight retry loops
                    // (e.g. NOT_MATCH_APP_INSTANCE_ID on dual-boot machines). Backoff is
                    // applied by the failed poll's markPollFailure, not by this DB write.
                    if (previous.connectState == SyncState.CONNECTED) {
                        emitEvent(SyncEvent.Resolve(current, createCallback()))
                    }
                }
                SyncState.INCOMPATIBLE,
                SyncState.UNMATCHED,
                SyncState.UNVERIFIED,
                -> {
                    emitEvent(SyncEvent.RefreshSyncInfo(current.appInstanceId, current.hostInfoList))
                }
                SyncState.CONNECTING -> {
                    // No event needed — the resolver completes the full
                    // discover → authenticate flow in a single pass.
                    // Polling handles stuck CONNECTING state (e.g. after app restart).
                }

                SyncState.CONNECTED -> {
                    syncPollingManager.reset()
                    // Immediately verify the connection. This covers the server-side
                    // trust flow where trustSyncInfo() writes CONNECTED to DB without
                    // the local device ever having sent a heartbeat to the remote peer.
                    emitEvent(SyncEvent.Resolve(current, createCallback()))
                }
            }
            return
        }

        if (!hostInfoListEqual(previous.hostInfoList, current.hostInfoList)) {
            if (current.connectState == SyncState.CONNECTED) {
                emitEvent(SyncEvent.Resolve(current, createCallback()))
            }
        }
    }

    override suspend fun getConnectHostAddress(): String? =
        currentSyncRuntimeInfo.connectHostAddress ?: run {
            val completionSignal = CompletableDeferred<Unit>()

            val callback =
                createCallback(
                    onComplete = {
                        completionSignal.complete(Unit)
                    },
                )

            emitEvent(SyncEvent.Resolve(currentSyncRuntimeInfo, callback))

            withTimeoutOrNull(5.seconds) {
                completionSignal.await()
            }

            currentSyncRuntimeInfo.connectHostAddress
        }

    override suspend fun forceResolve() {
        emitEvent(SyncEvent.ForceResolve(currentSyncRuntimeInfo, createCallback()))
    }

    override suspend fun fastReconnect() {
        // A paired-but-DISCONNECTED peer just proved reachable via mDNS serviceResolved.
        // Treat that reachability edge as a fresh start: clear the connection-failure
        // backoff so the polling fallback retries promptly if this immediate attempt
        // races the peer's server coming up, then drive an immediate reconnect.
        syncPollingManager.reset()
        forceResolve()
    }

    override suspend fun updateAllowSend(allowSend: Boolean) {
        emitEvent(SyncEvent.UpdateAllowSend(currentSyncRuntimeInfo, allowSend))
    }

    override suspend fun updateAllowReceive(allowReceive: Boolean) {
        emitEvent(SyncEvent.UpdateAllowReceive(currentSyncRuntimeInfo, allowReceive))
    }

    override suspend fun updateNoteName(noteName: String) {
        emitEvent(SyncEvent.UpdateNoteName(currentSyncRuntimeInfo, noteName))
    }

    override suspend fun trustByBearerToken(
        token: QrBearerToken,
        callback: (Boolean) -> Unit,
    ) {
        emitEvent(SyncEvent.TrustByBearerToken(currentSyncRuntimeInfo, token, callback))
    }

    override suspend fun trustBySasCode(
        code: SasCode,
        callback: (Boolean) -> Unit,
    ) {
        emitEvent(SyncEvent.TrustBySasCode(currentSyncRuntimeInfo, code, callback))
    }

    override suspend fun exchangeKeysForPairing() {
        emitEvent(SyncEvent.ExchangeKeysForPairing(currentSyncRuntimeInfo))
    }

    override suspend fun showToken() {
        emitEvent(SyncEvent.ShowToken(currentSyncRuntimeInfo))
    }

    override suspend fun showPairingCode() {
        emitEvent(SyncEvent.ShowPairingCode(currentSyncRuntimeInfo))
    }

    override suspend fun notifyExit() {
        val completionSignal = CompletableDeferred<Unit>()
        emitEvent(SyncEvent.NotifyExit(currentSyncRuntimeInfo, completionSignal))
        withTimeoutOrNull(5.seconds) {
            completionSignal.await()
        }
        cancelScope()
    }

    override suspend fun markExit() {
        emitEvent(SyncEvent.MarkExit(currentSyncRuntimeInfo))
    }

    override suspend fun removeDevice() {
        emitEvent(SyncEvent.RemoveDevice(currentSyncRuntimeInfo))
    }

    override fun cancelScope() {
        job.cancel()
        syncHandlerScope.cancel()
    }
}
