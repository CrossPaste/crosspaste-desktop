package com.clipevery.sync

import com.clipevery.dao.sync.SyncRuntimeInfo

interface SyncManager {

    var realTimeSyncRuntimeInfos: MutableList<SyncRuntimeInfo>

    fun resolveSyncs(force: Boolean)

    fun resolveSync(
        id: String,
        force: Boolean,
    )

    fun getSyncHandlers(): Map<String, SyncHandler>

    suspend fun trustByToken(
        appInstanceId: String,
        token: Int,
    )
}
