package com.clipevery.dao.sync

import com.clipevery.dto.sync.SyncInfo
import io.realm.kotlin.query.RealmResults

interface SyncRuntimeInfoDao {
    fun getAllSyncRuntimeInfos(): RealmResults<SyncRuntimeInfo>

    fun getSyncRuntimeInfo(appInstanceId: String): SyncRuntimeInfo?

    fun update(
        syncRuntimeInfo: SyncRuntimeInfo,
        block: SyncRuntimeInfo.() -> Unit,
    ): SyncRuntimeInfo?

    suspend fun suspendUpdate(
        syncRuntimeInfo: SyncRuntimeInfo,
        block: SyncRuntimeInfo.() -> Unit,
    ): SyncRuntimeInfo?

    fun inertOrUpdate(syncInfo: SyncInfo)

    fun inertOrUpdate(syncInfos: List<SyncInfo>): List<String>

    fun deleteSyncRuntimeInfo(appInstanceId: String)
}
