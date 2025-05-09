package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.net.VersionRelation

interface SyncHandler {

    var syncRuntimeInfo: SyncRuntimeInfo

    var versionRelation: VersionRelation

    suspend fun getConnectHostAddress(): String?

    suspend fun forceResolve()

    suspend fun updateSyncRuntimeInfo(doUpdate: (SyncRuntimeInfo) -> SyncRuntimeInfo): SyncRuntimeInfo?

    suspend fun tryDirectUpdateConnected()

    // use user input token to trust
    suspend fun trustByToken(token: Int)

    suspend fun showToken(syncRuntimeInfo: SyncRuntimeInfo)

    suspend fun notifyExit()

    suspend fun markExit()

    suspend fun clearContext()
}
