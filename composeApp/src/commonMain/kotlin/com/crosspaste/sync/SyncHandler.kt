package com.crosspaste.sync

import com.crosspaste.net.VersionRelation
import com.crosspaste.realm.sync.SyncRuntimeInfo

interface SyncHandler {

    var syncRuntimeInfo: SyncRuntimeInfo

    var versionRelation: VersionRelation

    suspend fun getConnectHostAddress(): String?

    suspend fun forceResolve()

    suspend fun update(block: SyncRuntimeInfo.() -> Unit): SyncRuntimeInfo?

    suspend fun tryDirectUpdateConnected()

    // use user input token to trust
    suspend fun trustByToken(token: Int)

    suspend fun showToken()

    suspend fun notifyExit()

    suspend fun markExit()

    suspend fun clearContext()
}
