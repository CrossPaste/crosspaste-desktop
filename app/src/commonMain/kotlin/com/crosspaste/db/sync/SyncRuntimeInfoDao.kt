package com.crosspaste.db.sync

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import com.crosspaste.Database
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.getJsonUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlin.reflect.KProperty1

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
                update(it)
            }
        }
    }

    fun update(syncRuntimeInfo: SyncRuntimeInfo, todo: () -> Unit = {}): String? {
        var change = false
        database.transactionWithResult {

            val hostInfoArrayJson = jsonUtils.JSON.encodeToString(syncRuntimeInfo.hostInfoList)

            change = syncRuntimeInfoDatabaseQueries.updateSyncRuntimeInfo(
                syncRuntimeInfo.appVersion,
                syncRuntimeInfo.userName,
                syncRuntimeInfo.deviceId,
                syncRuntimeInfo.deviceName,
                syncRuntimeInfo.platform.name,
                syncRuntimeInfo.platform.arch,
                syncRuntimeInfo.platform.bitMode.toLong(),
                syncRuntimeInfo.platform.version,
                hostInfoArrayJson,
                syncRuntimeInfo.port.toLong(),
                syncRuntimeInfo.noteName,
                syncRuntimeInfo.connectNetworkPrefixLength?.toLong(),
                syncRuntimeInfo.connectHostAddress,
                syncRuntimeInfo.connectState.toLong(),
                syncRuntimeInfo.allowSend,
                syncRuntimeInfo.allowReceive,
                nowEpochMilliseconds(),
                syncRuntimeInfo.appInstanceId,
            ).executeAsOneOrNull() != null

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
        database.transaction {
            syncRuntimeInfoDatabaseQueries.deleteSyncRuntimeInfo(appInstanceId)
        }
    }

    fun insertOrUpdateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo): ChangeType {
        return database.transactionWithResult {
            val now = nowEpochMilliseconds()
            val hostInfoArrayJson = jsonUtils.JSON.encodeToString(syncRuntimeInfo.hostInfoList)
            syncRuntimeInfoDatabaseQueries.getSyncRuntimeInfo(
                syncRuntimeInfo.appInstanceId,
                SyncRuntimeInfo::mapper,
            ).executeAsOneOrNull()?.let {
                syncRuntimeInfoDatabaseQueries.updateSyncRuntimeInfo(
                    syncRuntimeInfo.appVersion,
                    syncRuntimeInfo.userName,
                    syncRuntimeInfo.deviceId,
                    syncRuntimeInfo.deviceName,
                    syncRuntimeInfo.platform.name,
                    syncRuntimeInfo.platform.arch,
                    syncRuntimeInfo.platform.bitMode.toLong(),
                    syncRuntimeInfo.platform.version,
                    hostInfoArrayJson,
                    syncRuntimeInfo.port.toLong(),
                    syncRuntimeInfo.noteName,
                    syncRuntimeInfo.connectNetworkPrefixLength?.toLong(),
                    syncRuntimeInfo.connectHostAddress,
                    syncRuntimeInfo.connectState.toLong(),
                    syncRuntimeInfo.allowSend,
                    syncRuntimeInfo.allowReceive,
                    now,
                    syncRuntimeInfo.appInstanceId,
                )
                getChangeType(it, syncRuntimeInfo)
            } ?: run {
                syncRuntimeInfoDatabaseQueries.createSyncRuntimeInfo(
                    syncRuntimeInfo.appInstanceId,
                    syncRuntimeInfo.appVersion,
                    syncRuntimeInfo.userName,
                    syncRuntimeInfo.deviceId,
                    syncRuntimeInfo.deviceName,
                    syncRuntimeInfo.platform.name,
                    syncRuntimeInfo.platform.arch,
                    syncRuntimeInfo.platform.bitMode.toLong(),
                    syncRuntimeInfo.platform.version,
                    hostInfoArrayJson,
                    syncRuntimeInfo.port.toLong(),
                    syncRuntimeInfo.noteName,
                    syncRuntimeInfo.connectNetworkPrefixLength?.toLong(),
                    syncRuntimeInfo.connectHostAddress,
                    syncRuntimeInfo.connectState.toLong(),
                    syncRuntimeInfo.allowSend,
                    syncRuntimeInfo.allowReceive,
                    now,
                    now,
                )
                ChangeType.NEW_INSTANCE
            }
        }
    }

    private fun getChangeType(
        syncRuntimeInfo: SyncRuntimeInfo,
        newSyncRuntimeInfo: SyncRuntimeInfo,
    ): ChangeType {
        var netChange = false
        var infoChange = false

        fun <T> updateField(
            field: KProperty1<SyncRuntimeInfo, T>,
            isNetField: Boolean = false,
            customEquals: ((T, T) -> Boolean)? = null,
        ): Boolean {
            val oldValue = field.get(syncRuntimeInfo)
            val newValue = field.get(newSyncRuntimeInfo)
            val areEqual = customEquals?.invoke(oldValue, newValue) ?: (oldValue == newValue)
            return if (!areEqual) {
                if (isNetField) {
                    netChange = true
                } else {
                    infoChange = true
                }
                true
            } else {
                false
            }
        }

        // Update network-related fields
        updateField(SyncRuntimeInfo::hostInfoList, true, this::hostInfoListEqual)
        updateField(SyncRuntimeInfo::port, true)

        // Update info-related fields
        updateField(SyncRuntimeInfo::appVersion)
        updateField(SyncRuntimeInfo::userName)
        updateField(SyncRuntimeInfo::deviceId)
        updateField(SyncRuntimeInfo::deviceName)
        updateField(SyncRuntimeInfo::platform)

        return when {
            netChange -> ChangeType.NET_CHANGE
            infoChange -> ChangeType.INFO_CHANGE
            else -> ChangeType.NO_CHANGE
        }
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