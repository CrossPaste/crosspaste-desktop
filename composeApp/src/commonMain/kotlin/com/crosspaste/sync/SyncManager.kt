package com.crosspaste.sync

import com.crosspaste.dao.sync.SyncRuntimeInfo
import com.crosspaste.dto.sync.SyncInfo

interface SyncManager {

    var realTimeSyncRuntimeInfos: MutableList<SyncRuntimeInfo>

    var waitToVerifySyncRuntimeInfo: SyncRuntimeInfo?

    fun resolveSyncs()

    fun resolveSync(id: String)

    fun getSyncHandlers(): Map<String, SyncHandler>

    fun removeSyncHandler(id: String)

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
