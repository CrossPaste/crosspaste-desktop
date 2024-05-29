package com.clipevery.sync

import com.clipevery.dao.sync.SyncRuntimeInfo

interface SyncManager {

    var realTimeSyncRuntimeInfos: MutableList<SyncRuntimeInfo>

    var waitToVerifySyncRuntimeInfo: SyncRuntimeInfo?

    suspend fun resolveSyncs(resolveWay: ResolveWay)

    suspend fun resolveSync(
        id: String,
        resolveWay: ResolveWay,
    )

    fun getSyncHandlers(): Map<String, SyncHandler>

    fun refreshWaitToVerifySyncRuntimeInfo()

    fun ignoreVerify(appInstanceId: String)

    fun toVerify(appInstanceId: String)

    suspend fun trustByToken(
        appInstanceId: String,
        token: Int,
    )
}
