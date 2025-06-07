package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.net.VersionRelation

class MarketingSyncHandler(
    private var syncRuntimeInfo: SyncRuntimeInfo,
) : SyncHandler {

    override var versionRelation: VersionRelation = VersionRelation.EQUAL_TO

    override fun getCurrentSyncRuntimeInfo(): SyncRuntimeInfo {
        return syncRuntimeInfo
    }

    override suspend fun setCurrentSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo) {
        this.syncRuntimeInfo = syncRuntimeInfo
    }

    override suspend fun getConnectHostAddress(): String? {
        return syncRuntimeInfo.connectHostAddress
    }

    override suspend fun forceResolve() {
    }

    override suspend fun updateAllowSend(allowSend: Boolean): SyncRuntimeInfo? {
        return syncRuntimeInfo.copy(allowSend = allowSend)
    }

    override suspend fun updateAllowReceive(allowReceive: Boolean): SyncRuntimeInfo? {
        return syncRuntimeInfo.copy(allowSend = allowReceive)
    }

    override suspend fun updateNoteName(noteName: String): SyncRuntimeInfo? {
        return syncRuntimeInfo.copy(noteName = noteName)
    }

    override suspend fun tryDirectUpdateConnected() {
    }

    override suspend fun trustByToken(token: Int) {
    }

    override suspend fun showToken(syncRuntimeInfo: SyncRuntimeInfo) {
    }

    override suspend fun notifyExit() {
    }

    override suspend fun markExit() {
    }

    override suspend fun clearContext() {
    }
}
