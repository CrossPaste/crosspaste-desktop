package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.net.routing.SyncRoutingApi
import kotlinx.coroutines.flow.StateFlow

interface SyncManager : SyncRoutingApi {

    val realTimeSyncRuntimeInfos: StateFlow<List<SyncRuntimeInfo>>

    fun createSyncHandler(syncRuntimeInfo: SyncRuntimeInfo): SyncHandler

    fun ignoreVerify(appInstanceId: String)

    fun toVerify(appInstanceId: String)

    suspend fun resolveSyncs()

    suspend fun resolveSync(id: String)

    fun trustByToken(
        appInstanceId: String,
        token: Int,
    )

    fun refresh(ids: List<String> = listOf())
}
