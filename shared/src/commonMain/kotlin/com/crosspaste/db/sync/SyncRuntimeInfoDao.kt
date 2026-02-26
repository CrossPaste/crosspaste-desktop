package com.crosspaste.db.sync

import com.crosspaste.dto.sync.SyncInfo
import kotlinx.coroutines.flow.Flow

interface SyncRuntimeInfoDao {

    fun getAllSyncRuntimeInfosFlow(): Flow<List<SyncRuntimeInfo>>

    suspend fun getAllSyncRuntimeInfos(): List<SyncRuntimeInfo>

    suspend fun getSyncRuntimeInfo(appInstanceId: String): SyncRuntimeInfo?

    suspend fun updateConnectInfo(syncRuntimeInfo: SyncRuntimeInfo): String?

    suspend fun updateAllowReceive(syncRuntimeInfo: SyncRuntimeInfo): String?

    suspend fun updateAllowSend(syncRuntimeInfo: SyncRuntimeInfo): String?

    suspend fun updateNoteName(syncRuntimeInfo: SyncRuntimeInfo): String?

    suspend fun deleteSyncRuntimeInfo(appInstanceId: String)

    suspend fun insertOrUpdateSyncInfo(
        syncInfo: SyncInfo,
        connectInfo: ConnectInfo? = null,
    )
}
