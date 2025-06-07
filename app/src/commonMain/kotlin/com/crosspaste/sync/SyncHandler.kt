package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.net.VersionRelation

interface SyncHandler {

    var versionRelation: VersionRelation

    fun getCurrentSyncRuntimeInfo(): SyncRuntimeInfo

    suspend fun setCurrentSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo)

    suspend fun getConnectHostAddress(): String?

    suspend fun forceResolve()

    suspend fun updateAllowSend(allowSend: Boolean): SyncRuntimeInfo?

    suspend fun updateAllowReceive(allowReceive: Boolean): SyncRuntimeInfo?

    suspend fun updateNoteName(noteName: String): SyncRuntimeInfo?

    suspend fun tryDirectUpdateConnected()

    // use user input token to trust
    suspend fun trustByToken(token: Int)

    suspend fun showToken(syncRuntimeInfo: SyncRuntimeInfo)

    suspend fun notifyExit()

    suspend fun markExit()

    suspend fun clearContext()
}
