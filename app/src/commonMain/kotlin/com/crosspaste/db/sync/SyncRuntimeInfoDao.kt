package com.crosspaste.db.sync

import app.cash.sqldelight.Query
import com.crosspaste.Database
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.filter
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

class SyncRuntimeInfoDao(private val database: Database) {

    private val jsonUtils = getJsonUtils()

    private val syncRuntimeInfoDatabaseQueries = database.syncRuntimeInfoDatabaseQueries

    private val updateNotifier = Channel<String>(Channel.UNLIMITED)

    private fun createGetAllSyncRuntimeInfosQuery(): Query<SyncRuntimeInfo> {
        return syncRuntimeInfoDatabaseQueries.getAllSyncRuntimeInfos(SyncRuntimeInfo::mapper)
    }

    private fun cleanUpdateNotifier() {
        while (updateNotifier.tryReceive().isSuccess) {
            // do nothing
        }
    }

    fun getAllSyncRuntimeInfosFlow(): Flow<List<SyncRuntimeInfo>> {
        return flow {
            cleanUpdateNotifier()
            val currentMap = getAllSyncRuntimeInfos().associateBy { it.appInstanceId }.toMutableMap()
            emit(currentMap.values.toList())

            updateNotifier.receiveAsFlow().collect { appInstanceId ->
                val updated = syncRuntimeInfoDatabaseQueries.getSyncRuntimeInfo(
                    appInstanceId,
                    SyncRuntimeInfo::mapper
                ).executeAsOneOrNull()

                if (updated != null) {
                    currentMap[appInstanceId] = updated
                } else {
                    currentMap.remove(appInstanceId)
                }

                emit(currentMap.values.toList())
            }
        }.flowOn(ioDispatcher)
    }

    suspend fun getAllSyncRuntimeInfos(): List<SyncRuntimeInfo> = withContext(ioDispatcher) {
        createGetAllSyncRuntimeInfosQuery().executeAsList()
    }

    suspend fun getSyncRuntimeInfo(appInstanceId: String): SyncRuntimeInfo? = withContext(ioDispatcher) {
        syncRuntimeInfoDatabaseQueries.getSyncRuntimeInfo(
            appInstanceId,
            SyncRuntimeInfo::mapper,
        ).executeAsOneOrNull()
    }

    private suspend fun updateTemplate(
        syncRuntimeInfo: SyncRuntimeInfo,
        updateAction: (SyncRuntimeInfo) -> Boolean,
    ): String? = withContext(ioDispatcher) {
        val change = database.transactionWithResult {
            updateAction(syncRuntimeInfo)
        }
        if (change) {
            updateNotifier.trySend(syncRuntimeInfo.appInstanceId)
            syncRuntimeInfo.appInstanceId
        } else {
            null
        }
    }

    suspend fun updateConnectInfo(syncRuntimeInfo: SyncRuntimeInfo): String? {
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

    suspend fun updateAllowReceive(syncRuntimeInfo: SyncRuntimeInfo): String? {
        return updateTemplate(syncRuntimeInfo) {
            syncRuntimeInfoDatabaseQueries.updateAllowReceive(
                syncRuntimeInfo.allowReceive,
                nowEpochMilliseconds(),
                syncRuntimeInfo.appInstanceId,
            )
            syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
        }
    }

    suspend fun updateAllowSend(syncRuntimeInfo: SyncRuntimeInfo): String? {
        return updateTemplate(syncRuntimeInfo) {
            syncRuntimeInfoDatabaseQueries.updateAllowSend(
                syncRuntimeInfo.allowSend,
                nowEpochMilliseconds(),
                syncRuntimeInfo.appInstanceId,
            )
            syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
        }
    }

    suspend fun updateNoteName(syncRuntimeInfo: SyncRuntimeInfo): String? {
        return updateTemplate(syncRuntimeInfo) {
            syncRuntimeInfoDatabaseQueries.updateNoteName(
                syncRuntimeInfo.noteName,
                nowEpochMilliseconds(),
                syncRuntimeInfo.appInstanceId,
            )
            syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
        }
    }

    suspend fun deleteSyncRuntimeInfo(appInstanceId: String) = withContext(ioDispatcher) {
        val deleted = database.transactionWithResult {
            syncRuntimeInfoDatabaseQueries.deleteSyncRuntimeInfo(appInstanceId)
            syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
        }
        if (deleted) {
            updateNotifier.trySend(appInstanceId)
        }
    }

    // only use in GeneralSyncManager，if want to insertOrUpdateSyncInfo SyncRuntimeInfo
    // use SyncManager.updateSyncInfo，it will refresh connect state
    suspend fun insertOrUpdateSyncInfo(
        syncInfo: SyncInfo,
        host: String? = null,
    ) = withContext(ioDispatcher) {
        val changed = database.transactionWithResult {
            val now = nowEpochMilliseconds()
            syncRuntimeInfoDatabaseQueries.getSyncRuntimeInfo(
                syncInfo.appInfo.appInstanceId,
                SyncRuntimeInfo::mapper,
            ).executeAsOneOrNull()?.let { existing ->
                val hostInfoList = (existing.hostInfoList + syncInfo.endpointInfo.hostInfoList).distinct()

                var connectNetworkPrefixLength: Long? = null
                var connectHostAddress: String? = null
                var connectState: Long = -1L

                if (host != null) {
                    for (hostInfo in hostInfoList) {
                        if (hostInfo.filter(host)) {
                            connectNetworkPrefixLength = hostInfo.networkPrefixLength.toLong()
                            connectHostAddress = hostInfo.hostAddress
                            connectState = SyncState.CONNECTED.toLong()
                            break
                        }
                    }
                }

                val hostInfoChanged = !SyncRuntimeInfo.hostInfoListEqual(existing.hostInfoList, hostInfoList)
                val syncInfoChanged = existing.diffSyncInfo(syncInfo)
                val connectChanged = host != null && (
                    existing.connectHostAddress != connectHostAddress ||
                    existing.connectNetworkPrefixLength?.toLong() != connectNetworkPrefixLength ||
                    existing.connectState.toLong() != connectState
                )

                if (!hostInfoChanged && !syncInfoChanged && !connectChanged) {
                    return@transactionWithResult false
                }

                val hostInfoArrayJson = jsonUtils.JSON.encodeToString(hostInfoList)
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
                    syncInfo.endpointInfo.port.toLong(),
                    connectNetworkPrefixLength,
                    connectHostAddress,
                    connectState,
                    connectState,
                    now,
                    syncInfo.appInfo.appInstanceId,
                )
                true
            } ?: run {
                var connectNetworkPrefixLength: Long? = null
                var connectHostAddress: String? = null
                var connectState: Long = SyncState.DISCONNECTED.toLong()

                if (host != null) {
                    for (hostInfo in syncInfo.endpointInfo.hostInfoList) {
                        if (hostInfo.filter(host)) {
                            connectNetworkPrefixLength = hostInfo.networkPrefixLength.toLong()
                            connectHostAddress = hostInfo.hostAddress
                            connectState = SyncState.CONNECTED.toLong()
                            break
                        }
                    }
                }

                val hostInfoArrayJson = jsonUtils.JSON.encodeToString(syncInfo.endpointInfo.hostInfoList)
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
                    connectNetworkPrefixLength,
                    connectHostAddress,
                    connectState,
                    now,
                    now,
                )
                true
            }
        }

        if (changed) {
            updateNotifier.trySend(syncInfo.appInfo.appInstanceId)
        }
    }
}