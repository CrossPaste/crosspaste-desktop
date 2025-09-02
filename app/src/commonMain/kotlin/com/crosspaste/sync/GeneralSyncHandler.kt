package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfo.Companion.hostInfoListEqual
import com.crosspaste.db.sync.SyncState
import com.crosspaste.net.VersionRelation
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch

class GeneralSyncHandler(
    syncRuntimeInfo: SyncRuntimeInfo,
    private val emitEvent: suspend (SyncEvent) -> Unit,
    private val syncHandlerScope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob()),
) : SyncHandler {

    private val _syncRuntimeInfo: MutableStateFlow<SyncRuntimeInfo> = MutableStateFlow(syncRuntimeInfo)

    override val syncRuntimeInfoFlow: StateFlow<SyncRuntimeInfo> = _syncRuntimeInfo

    private val _versionRelation: MutableStateFlow<VersionRelation> = MutableStateFlow(VersionRelation.EQUAL_TO)

    override var versionRelation: StateFlow<VersionRelation> = _versionRelation

    private val syncPollingManager = SyncPollingManager(syncHandlerScope)

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
                emitEvent(SyncEvent.ResolveConnection(currentSyncRuntimeInfo, ::updateVersionRelation))
            }
    }

    override fun updateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo) {
        _syncRuntimeInfo.value = syncRuntimeInfo
    }

    private fun updateVersionRelation(relation: VersionRelation) {
        _versionRelation.value = relation
    }

    private suspend fun handleFirstValue(syncRuntimeInfo: SyncRuntimeInfo) {
        when (syncRuntimeInfo.connectState) {
            SyncState.DISCONNECTED,
            SyncState.INCOMPATIBLE,
            SyncState.UNMATCHED,
            SyncState.UNVERIFIED,
            -> {
                emitEvent(SyncEvent.ResolveDisconnected(syncRuntimeInfo, ::updateVersionRelation))
            }
            SyncState.CONNECTING -> {
                emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, ::updateVersionRelation))
            }
            SyncState.CONNECTED -> {
                emitEvent(SyncEvent.ResolveConnection(syncRuntimeInfo, ::updateVersionRelation))
            }
        }
    }

    private suspend fun handleValueChange(
        previous: SyncRuntimeInfo,
        current: SyncRuntimeInfo,
    ) {
        if (current.port != previous.port) {
            emitEvent(SyncEvent.ResolveConnection(current, ::updateVersionRelation))
            return
        }

        if (previous.connectHostAddress != null &&
            current.connectHostAddress != null &&
            previous.connectHostAddress != current.connectHostAddress
        ) {
            emitEvent(SyncEvent.ResolveConnection(current, ::updateVersionRelation))
            return
        }

        if (current.connectState != previous.connectState) {
            when (current.connectState) {
                SyncState.DISCONNECTED,
                SyncState.INCOMPATIBLE,
                SyncState.UNMATCHED,
                SyncState.UNVERIFIED,
                -> {
                    syncPollingManager.fail()
                    emitEvent(SyncEvent.RefreshSyncInfo(current.appInstanceId))
                }
                SyncState.CONNECTING -> {
                    emitEvent(SyncEvent.ResolveConnecting(current, ::updateVersionRelation))
                }

                SyncState.CONNECTED -> {
                    syncPollingManager.reset()
                    emitEvent(SyncEvent.ResolveConnection(current, ::updateVersionRelation))
                }
            }
        }

        if (!hostInfoListEqual(previous.hostInfoList, current.hostInfoList)) {
            if (current.connectState == SyncState.CONNECTED) {
                emitEvent(SyncEvent.ResolveConnection(current, ::updateVersionRelation))
            }
        }

        when (current.connectState) {
            SyncState.DISCONNECTED,
            SyncState.INCOMPATIBLE,
            SyncState.UNMATCHED,
            SyncState.UNVERIFIED,
            -> {
                syncPollingManager.fail()
            }
        }
    }

    override suspend fun getConnectHostAddress(): String? =
        currentSyncRuntimeInfo.connectHostAddress ?: run {
            emitEvent(SyncEvent.ResolveConnection(currentSyncRuntimeInfo, ::updateVersionRelation))
            delay(1000)
            currentSyncRuntimeInfo.connectHostAddress
        }

    override suspend fun forceResolve() {
        emitEvent(SyncEvent.ForceResolveConnection(currentSyncRuntimeInfo, ::updateVersionRelation))
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

    override suspend fun trustByToken(token: Int) {
        emitEvent(SyncEvent.TrustByToken(currentSyncRuntimeInfo, token))
    }

    override suspend fun showToken() {
        emitEvent(SyncEvent.ShowToken(currentSyncRuntimeInfo))
    }

    override suspend fun notifyExit() {
        emitEvent(SyncEvent.NotifyExit(currentSyncRuntimeInfo))
        job.cancel()
        syncHandlerScope.cancel()
    }

    override suspend fun markExit() {
        emitEvent(SyncEvent.MarkExit(currentSyncRuntimeInfo))
    }

    override suspend fun removeDevice() {
        emitEvent(SyncEvent.RemoveDevice(currentSyncRuntimeInfo))
        job.cancel()
        syncHandlerScope.cancel()
    }
}
