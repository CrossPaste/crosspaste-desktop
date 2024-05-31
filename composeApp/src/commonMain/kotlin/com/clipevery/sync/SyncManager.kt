package com.clipevery.sync

import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dto.sync.SyncInfo

interface SyncManager {

    var realTimeSyncRuntimeInfos: MutableList<SyncRuntimeInfo>

    var waitToVerifySyncRuntimeInfo: SyncRuntimeInfo?

    suspend fun resolveSyncs()

    suspend fun resolveSync(id: String)

    fun getSyncHandlers(): Map<String, SyncHandler>

    fun refreshWaitToVerifySyncRuntimeInfo()

    fun ignoreVerify(appInstanceId: String)

    fun toVerify(appInstanceId: String)

    suspend fun trustByToken(
        appInstanceId: String,
        token: Int,
    )

    fun notifyExit()

    fun markExit(appInstanceId: String)

    fun updateSyncInfo(syncInfo: SyncInfo)
}
