package com.crosspaste.db.sync

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import com.crosspaste.Database
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.getJsonUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SyncRuntimeInfoDao(private val database: Database) {

    private val jsonUtils = getJsonUtils()

    private val syncRuntimeInfoDatabaseQueries = database.syncRuntimeInfoDatabaseQueries

    private fun createGetAllSyncRuntimeInfosQuery(): Query<SyncRuntimeInfo> {
        return syncRuntimeInfoDatabaseQueries.getAllSyncRuntimeInfos(SyncRuntimeInfo::mapper)
    }

    fun getAllSyncRuntimeInfosFlow(): Flow<List<SyncRuntimeInfo>> {
        return createGetAllSyncRuntimeInfosQuery().asFlow()
            .map { it.executeAsList() }
    }

    fun getAllSyncRuntimeInfos(): List<SyncRuntimeInfo> {
        return createGetAllSyncRuntimeInfosQuery().executeAsList()
    }

    private fun updateTemplate(
        syncRuntimeInfo: SyncRuntimeInfo,
        updateAction: (SyncRuntimeInfo) -> Boolean,
    ): String? {
        val change = database.transactionWithResult {
            updateAction(syncRuntimeInfo)
        }
        return if (change) {
            syncRuntimeInfo.appInstanceId
        } else {
            null
        }
    }

    fun updateConnectInfo(syncRuntimeInfo: SyncRuntimeInfo): String? {
        return updateTemplate(syncRuntimeInfo) {
            syncRuntimeInfoDatabaseQueries.updateConnectInfo(
                syncRuntimeInfo.port.toLong(),
                syncRuntimeInfo.connectNetworkPrefixLength?.toLong(),
                syncRuntimeInfo.connectHostAddress,
                syncRuntimeInfo.connectState.toLong(),
                nowEpochMilliseconds(),
                syncRuntimeInfo.appInstanceId,
            )
            syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
        }
    }

    fun updateAllowReceive(syncRuntimeInfo: SyncRuntimeInfo): String? {
        return updateTemplate(syncRuntimeInfo) {
            syncRuntimeInfoDatabaseQueries.updateAllowReceive(
                syncRuntimeInfo.allowReceive,
                nowEpochMilliseconds(),
                syncRuntimeInfo.appInstanceId,
            )
            syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
        }
    }

    fun updateAllowSend(syncRuntimeInfo: SyncRuntimeInfo): String? {
        return updateTemplate(syncRuntimeInfo) {
            syncRuntimeInfoDatabaseQueries.updateAllowSend(
                syncRuntimeInfo.allowSend,
                nowEpochMilliseconds(),
                syncRuntimeInfo.appInstanceId,
            )
            syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
        }
    }

    fun updateNoteName(syncRuntimeInfo: SyncRuntimeInfo): String? {
        return updateTemplate(syncRuntimeInfo) {
            syncRuntimeInfoDatabaseQueries.updateNoteName(
                syncRuntimeInfo.noteName,
                nowEpochMilliseconds(),
                syncRuntimeInfo.appInstanceId,
            )
            syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
        }
    }

    fun deleteSyncRuntimeInfo(appInstanceId: String) {
        syncRuntimeInfoDatabaseQueries.deleteSyncRuntimeInfo(appInstanceId)
    }

    // only use in GeneralSyncManager，if want to insertOrUpdateSyncInfo SyncRuntimeInfo
    // use SyncManager.updateSyncInfo，it will refresh connect state
    fun insertOrUpdateSyncInfo(syncInfo: SyncInfo) {
        return database.transactionWithResult {
            val now = nowEpochMilliseconds()
            val hostInfoArrayJson = jsonUtils.JSON.encodeToString(syncInfo.endpointInfo.hostInfoList)
            syncRuntimeInfoDatabaseQueries.getSyncRuntimeInfo(
                syncInfo.appInfo.appInstanceId,
                SyncRuntimeInfo::mapper,
            ).executeAsOneOrNull()?.let {
                syncRuntimeInfoDatabaseQueries.updateSyncInfo(
                    syncInfo.appInfo.appVersion,
                    syncInfo.appInfo.userName,
                    syncInfo.endpointInfo.deviceId,
                    syncInfo.endpointInfo.deviceName,
                    syncInfo.endpointInfo.platform.name,
                    syncInfo.endpointInfo.platform.arch,
                    syncInfo.endpointInfo.platform.bitMode.toLong(),
                    syncInfo.endpointInfo.platform.version,
                    hostInfoArrayJson,
                    syncInfo.endpointInfo.port.toLong(),
                    now,
                    syncInfo.appInfo.appInstanceId,
                )
            } ?: run {
                syncRuntimeInfoDatabaseQueries.createSyncRuntimeInfo(
                    syncInfo.appInfo.appInstanceId,
                    syncInfo.appInfo.appVersion,
                    syncInfo.appInfo.userName,
                    syncInfo.endpointInfo.deviceId,
                    syncInfo.endpointInfo.deviceName,
                    syncInfo.endpointInfo.platform.name,
                    syncInfo.endpointInfo.platform.arch,
                    syncInfo.endpointInfo.platform.bitMode.toLong(),
                    syncInfo.endpointInfo.platform.version,
                    hostInfoArrayJson,
                    syncInfo.endpointInfo.port.toLong(),
                    SyncState.DISCONNECTED.toLong(),
                    now,
                    now,
                )
            }
        }
    }
}