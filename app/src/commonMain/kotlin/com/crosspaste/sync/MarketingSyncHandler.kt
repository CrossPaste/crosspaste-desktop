package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.net.VersionRelation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MarketingSyncHandler(
    syncRuntimeInfo: SyncRuntimeInfo,
) : SyncHandler {

    private val _syncRuntimeInfo: MutableStateFlow<SyncRuntimeInfo> = MutableStateFlow(syncRuntimeInfo)

    override val syncRuntimeInfoFlow: StateFlow<SyncRuntimeInfo> = _syncRuntimeInfo

    private val _versionRelation: MutableStateFlow<VersionRelation> = MutableStateFlow(VersionRelation.EQUAL_TO)

    override var versionRelation: StateFlow<VersionRelation> = _versionRelation

    override fun updateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo) {
        _syncRuntimeInfo.value = syncRuntimeInfo
    }

    override suspend fun getConnectHostAddress(): String? = currentSyncRuntimeInfo.connectHostAddress

    override suspend fun forceResolve() {
    }

    override suspend fun updateAllowSend(allowSend: Boolean) {
    }

    override suspend fun updateAllowReceive(allowReceive: Boolean) {
    }

    override suspend fun updateNoteName(noteName: String) {
    }

    override suspend fun trustByToken(token: Int) {
    }

    override suspend fun showToken() {
    }

    override suspend fun notifyExit() {
    }

    override suspend fun markExit() {
    }

    override suspend fun removeDevice() {
    }
}
