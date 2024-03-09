package com.clipevery.sync

import com.clipevery.dao.sync.SyncRuntimeInfo

interface SyncHandler {

    var syncRuntimeInfo: SyncRuntimeInfo

    suspend fun getConnectHostAddress(): String?

    suspend fun resolveSync(force: Boolean)

    fun updateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo)

    fun clearContext()
}
