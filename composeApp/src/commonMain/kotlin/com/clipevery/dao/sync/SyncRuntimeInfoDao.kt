package com.clipevery.dao.sync

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

    fun insertOrUpdate(syncRuntimeInfo: SyncRuntimeInfo): Boolean

    fun deleteSyncRuntimeInfo(appInstanceId: String)
}
