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

    override fun updateAllowSend(
        allowSend: Boolean,
        callback: (SyncRuntimeInfo?) -> Unit,
    ) {
        callback(syncRuntimeInfo.copy(allowSend = allowSend))
    }

    override fun updateAllowReceive(
        allowReceive: Boolean,
        callback: (SyncRuntimeInfo?) -> Unit,
    ) {
        callback(syncRuntimeInfo.copy(allowReceive = allowReceive))
    }

    override fun updateNoteName(
        noteName: String,
        callback: (SyncRuntimeInfo?) -> Unit,
    ) {
        callback(syncRuntimeInfo.copy(noteName = noteName))
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
