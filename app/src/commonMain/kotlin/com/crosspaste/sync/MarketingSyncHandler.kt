package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.net.VersionRelation

class MarketingSyncHandler(
    override var syncRuntimeInfo: SyncRuntimeInfo,
) : SyncHandler {

    override var versionRelation: VersionRelation = VersionRelation.EQUAL_TO

    override suspend fun getConnectHostAddress(): String? {
        return syncRuntimeInfo.connectHostAddress
    }

    override suspend fun forceResolve() {
    }

    override suspend fun update(block: (SyncRuntimeInfo) -> SyncRuntimeInfo): SyncRuntimeInfo? {
        return syncRuntimeInfo
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
