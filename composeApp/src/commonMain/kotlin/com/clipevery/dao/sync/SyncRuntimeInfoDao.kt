package com.clipevery.dao.sync

import com.clipevery.dto.sync.SyncInfo

interface SyncRuntimeInfoDao {
    fun getAllSyncRuntimeInfos(): List<SyncRuntimeInfo>

    fun getSyncRuntimeInfo(appInstanceId: String): SyncRuntimeInfo?

    suspend fun getSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo): SyncRuntimeInfo?

    suspend fun updateConnectState(syncRuntimeInfo: SyncRuntimeInfo, connectState: Int)

    suspend fun updateConnectInfo(syncRuntimeInfo: SyncRuntimeInfo, connectState: Int, connectHostAddress: String)

    fun updateAllowSend(syncRuntimeInfo: SyncRuntimeInfo, allowSend: Boolean): SyncRuntimeInfo?

    fun updateAllowReceive(syncRuntimeInfo: SyncRuntimeInfo, allowReceive: Boolean): SyncRuntimeInfo?

    fun inertOrUpdate(syncInfo: SyncInfo)

    fun inertOrUpdate(syncInfos: List<SyncInfo>): List<String>

    fun deleteSyncRuntimeInfo(appInstanceId: String)
}
