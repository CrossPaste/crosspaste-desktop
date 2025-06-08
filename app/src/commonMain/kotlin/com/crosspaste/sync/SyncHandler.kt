package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.net.VersionRelation

interface SyncHandler {

    var versionRelation: VersionRelation

    fun getCurrentSyncRuntimeInfo(): SyncRuntimeInfo

    suspend fun setCurrentSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo)

    suspend fun getConnectHostAddress(): String?

    suspend fun forceResolve()

    fun updateAllowSend(
        allowSend: Boolean,
        callback: (SyncRuntimeInfo?) -> Unit,
    )

    fun updateAllowReceive(
        allowReceive: Boolean,
        callback: (SyncRuntimeInfo?) -> Unit,
    )

    fun updateNoteName(
        noteName: String,
        callback: (SyncRuntimeInfo?) -> Unit,
    )

    suspend fun tryDirectUpdateConnected()

    // use user input token to trust
    suspend fun trustByToken(token: Int)

    suspend fun showToken(syncRuntimeInfo: SyncRuntimeInfo)

    suspend fun notifyExit()

    suspend fun markExit()

    suspend fun clearContext()
}
