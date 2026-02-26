package com.crosspaste.db.sync

import com.crosspaste.Database
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

class SqlSyncRuntimeInfoDao(
    private val database: Database,
) : SyncRuntimeInfoDao {

    private val jsonUtils = getJsonUtils()

    private val syncRuntimeInfoDatabaseQueries = database.syncRuntimeInfoDatabaseQueries

    private val updateNotifier = Channel<String>(Channel.UNLIMITED)

    private fun cleanUpdateNotifier() {
        while (updateNotifier.tryReceive().isSuccess) {
            // do nothing
        }
    }

    override fun getAllSyncRuntimeInfosFlow(): Flow<List<SyncRuntimeInfo>> =
        flow {
            cleanUpdateNotifier()
            val currentMap = getAllSyncRuntimeInfos().associateBy { it.appInstanceId }.toMutableMap()
            emit(currentMap.values.toList())

            updateNotifier.receiveAsFlow().collect { appInstanceId ->
                val updated =
                    syncRuntimeInfoDatabaseQueries
                        .getSyncRuntimeInfo(
                            appInstanceId,
                            SyncRuntimeInfo::mapper,
                        ).executeAsOneOrNull()

                if (updated != null) {
                    currentMap[appInstanceId] = updated
                } else {
                    currentMap.remove(appInstanceId)
                }

                emit(currentMap.values.toList())
            }
        }.flowOn(ioDispatcher)

    override suspend fun getAllSyncRuntimeInfos(): List<SyncRuntimeInfo> =
        withContext(ioDispatcher) {
            syncRuntimeInfoDatabaseQueries.getAllSyncRuntimeInfos(SyncRuntimeInfo::mapper).executeAsList()
        }

    override suspend fun getSyncRuntimeInfo(appInstanceId: String): SyncRuntimeInfo? =
        withContext(ioDispatcher) {
            syncRuntimeInfoDatabaseQueries
                .getSyncRuntimeInfo(
                    appInstanceId,
                    SyncRuntimeInfo::mapper,
                ).executeAsOneOrNull()
        }

    private suspend fun updateTemplate(
        syncRuntimeInfo: SyncRuntimeInfo,
        updateAction: (SyncRuntimeInfo) -> Boolean,
    ): String? =
        withContext(ioDispatcher) {
            val change =
                database.transactionWithResult {
                    updateAction(syncRuntimeInfo)
                }
            if (change) {
                updateNotifier.trySend(syncRuntimeInfo.appInstanceId)
                syncRuntimeInfo.appInstanceId
            } else {
                null
            }
        }

    override suspend fun updateConnectInfo(syncRuntimeInfo: SyncRuntimeInfo): String? =
        updateTemplate(syncRuntimeInfo) {
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

    override suspend fun updateAllowReceive(syncRuntimeInfo: SyncRuntimeInfo): String? =
        updateTemplate(syncRuntimeInfo) {
            syncRuntimeInfoDatabaseQueries.updateAllowReceive(
                syncRuntimeInfo.allowReceive,
                nowEpochMilliseconds(),
                syncRuntimeInfo.appInstanceId,
            )
            syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
        }

    override suspend fun updateAllowSend(syncRuntimeInfo: SyncRuntimeInfo): String? =
        updateTemplate(syncRuntimeInfo) {
            syncRuntimeInfoDatabaseQueries.updateAllowSend(
                syncRuntimeInfo.allowSend,
                nowEpochMilliseconds(),
                syncRuntimeInfo.appInstanceId,
            )
            syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
        }

    override suspend fun updateNoteName(syncRuntimeInfo: SyncRuntimeInfo): String? =
        updateTemplate(syncRuntimeInfo) {
            syncRuntimeInfoDatabaseQueries.updateNoteName(
                syncRuntimeInfo.noteName,
                nowEpochMilliseconds(),
                syncRuntimeInfo.appInstanceId,
            )
            syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
        }

    override suspend fun deleteSyncRuntimeInfo(appInstanceId: String) {
        withContext(ioDispatcher) {
            val deleted =
                database.transactionWithResult {
                    syncRuntimeInfoDatabaseQueries.deleteSyncRuntimeInfo(appInstanceId)
                    syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
                }
            if (deleted) {
                updateNotifier.trySend(appInstanceId)
            }
        }
    }

    // only use in GeneralSyncManager，if want to insertOrUpdateSyncInfo SyncRuntimeInfo
    // use SyncManager.updateSyncInfo，it will refresh connect state
    override suspend fun insertOrUpdateSyncInfo(
        syncInfo: SyncInfo,
        connectInfo: ConnectInfo?,
    ) {
        withContext(ioDispatcher) {
            val changed =
                database.transactionWithResult {
                    val now = nowEpochMilliseconds()
                    val existing =
                        syncRuntimeInfoDatabaseQueries
                            .getSyncRuntimeInfo(
                                syncInfo.appInfo.appInstanceId,
                                SyncRuntimeInfo::mapper,
                            ).executeAsOneOrNull()

                    if (existing != null) {
                        val hostInfoList = (existing.hostInfoList + syncInfo.endpointInfo.hostInfoList).distinct()

                        val connectNetworkPrefixLength: Long? = connectInfo?.networkPrefixLength?.toLong()
                        val connectHostAddress: String? = connectInfo?.hostAddress
                        val connectState: Long = if (connectInfo != null) SyncState.CONNECTED.toLong() else -1L

                        val hostInfoChanged =
                            !SyncRuntimeInfo.hostInfoListEqual(
                                existing.hostInfoList,
                                hostInfoList,
                            )
                        val syncInfoChanged = existing.diffSyncInfo(syncInfo)
                        val connectChanged =
                            connectInfo != null &&
                                (
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
                            syncInfo.endpointInfo.platform.bitMode
                                .toLong(),
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
                    } else {
                        val connectNetworkPrefixLength: Long? = connectInfo?.networkPrefixLength?.toLong()
                        val connectHostAddress: String? = connectInfo?.hostAddress
                        val connectState: Long =
                            if (connectInfo != null) SyncState.CONNECTED.toLong() else SyncState.DISCONNECTED.toLong()

                        val hostInfoArrayJson = jsonUtils.JSON.encodeToString(syncInfo.endpointInfo.hostInfoList)
                        syncRuntimeInfoDatabaseQueries.createSyncRuntimeInfo(
                            syncInfo.appInfo.appInstanceId,
                            syncInfo.appInfo.appVersion,
                            syncInfo.appInfo.userName,
                            syncInfo.endpointInfo.deviceId,
                            syncInfo.endpointInfo.deviceName,
                            syncInfo.endpointInfo.platform.name,
                            syncInfo.endpointInfo.platform.arch,
                            syncInfo.endpointInfo.platform.bitMode
                                .toLong(),
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
}
