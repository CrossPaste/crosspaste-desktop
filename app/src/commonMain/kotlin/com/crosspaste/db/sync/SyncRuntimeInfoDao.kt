package com.crosspaste.db.sync

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import com.crosspaste.Database
import com.crosspaste.db.sync.SyncRuntimeInfo.Companion.createSyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfo.Companion.updateSyncRuntimeInfo
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

    fun updateList(syncRuntimeInfoList: List<SyncRuntimeInfo>): List<String> {
        return database.transactionWithResult {
            syncRuntimeInfoList.mapNotNull {
                updateConnectInfo(it)
            }
        }
    }

    fun updateConnectInfo(syncRuntimeInfo: SyncRuntimeInfo, todo: () -> Unit = {}): String? {
        var change = false
        database.transactionWithResult {
            syncRuntimeInfoDatabaseQueries.updateConnectInfo(
                syncRuntimeInfo.port.toLong(),
                syncRuntimeInfo.connectNetworkPrefixLength?.toLong(),
                syncRuntimeInfo.connectHostAddress,
                syncRuntimeInfo.connectState.toLong(),
                nowEpochMilliseconds(),
                syncRuntimeInfo.appInstanceId,
            )
            change = syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
            if (change) {
                todo()
            }
        }
        return if (change) {
            syncRuntimeInfo.appInstanceId
        } else {
            null
        }
    }

    fun updateNoteName(appInstanceId: String, noteName: String?) {
        syncRuntimeInfoDatabaseQueries.updateNoteName(
            noteName,
            nowEpochMilliseconds(),
            appInstanceId,
        )
    }

    fun deleteSyncRuntimeInfo(appInstanceId: String) {
        syncRuntimeInfoDatabaseQueries.deleteSyncRuntimeInfo(appInstanceId)
    }

    // only use in GeneralSyncManager，if want to insertOrUpdateSyncInfo SyncRuntimeInfo
    // use SyncManager.updateSyncInfo，it will refresh connect state
    fun insertOrUpdateSyncInfo(syncInfo: SyncInfo): Pair<ChangeType, SyncRuntimeInfo> {
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
                getChangeType(it, syncInfo)
            } ?: run {
                val syncRuntimeInfo = createSyncRuntimeInfo(syncInfo)
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
                Pair(ChangeType.NEW_INSTANCE, syncRuntimeInfo)
            }
        }
    }

    private fun getChangeType(
        syncRuntimeInfo: SyncRuntimeInfo,
        newSyncInfo: SyncInfo,
    ): Pair<ChangeType, SyncRuntimeInfo> {
        val changeType = if (
            hostInfoListEqual(syncRuntimeInfo.hostInfoList, newSyncInfo.endpointInfo.hostInfoList) ||
            syncRuntimeInfo.port != newSyncInfo.endpointInfo.port)
        {
            ChangeType.NET_CHANGE
        } else if (syncRuntimeInfo.appVersion != newSyncInfo.appInfo.appVersion ||
            syncRuntimeInfo.userName != newSyncInfo.appInfo.userName ||
            syncRuntimeInfo.deviceId != newSyncInfo.endpointInfo.deviceId ||
            syncRuntimeInfo.deviceName != newSyncInfo.endpointInfo.deviceName ||
            syncRuntimeInfo.platform != newSyncInfo.endpointInfo.platform)
        {
            ChangeType.INFO_CHANGE
        } else {
            ChangeType.NO_CHANGE
        }

        return Pair(changeType, updateSyncRuntimeInfo(syncRuntimeInfo, newSyncInfo))
    }

    private fun hostInfoListEqual(
        hostInfoList: List<HostInfo>,
        otherHostInfoList: List<HostInfo>,
    ): Boolean {
        if (hostInfoList.size != otherHostInfoList.size) {
            return false
        }
        val sortHostInfoList = hostInfoList.sortedWith { o1, o2 -> o1.hostAddress.compareTo(o2.hostAddress) }
        val otherSortHostInfoList = otherHostInfoList.sortedWith { o1, o2 -> o1.hostAddress.compareTo(o2.hostAddress) }
        for (i in 0 until hostInfoList.size) {
            if (sortHostInfoList[i].hostAddress != otherSortHostInfoList[i].hostAddress) {
                return false
            }
        }
        return true
    }

}