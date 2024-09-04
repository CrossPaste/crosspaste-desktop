package com.crosspaste.dao.sync

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

    fun insertOrUpdate(syncRuntimeInfo: SyncRuntimeInfo): ChangeType

    fun update(syncRuntimeInfos: List<SyncRuntimeInfo>): List<String>

    fun deleteSyncRuntimeInfo(appInstanceId: String)
}

enum class ChangeType {
    NEW_INSTANCE,
    NO_CHANGE,
    NET_CHANGE,
    INFO_CHANGE,
}
