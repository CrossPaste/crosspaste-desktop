package com.crosspaste.db.sync

import com.crosspaste.Database
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SqlSyncRuntimeInfoDao(
    private val database: Database,
) : SyncRuntimeInfoDao {

    private val jsonUtils = getJsonUtils()

    private val syncRuntimeInfoDatabaseQueries = database.syncRuntimeInfoDatabaseQueries

    private val writeMutex = Mutex()

    /**
     * Broadcast "table changed" signal — multi-subscriber safe.
     *
     * Previously this was a `Channel<String>(UNLIMITED)`, which has fan-out
     * (each item delivered to exactly one collector). With a single subscriber
     * (`GeneralSyncManager`) that worked, but adding a second subscriber
     * (e.g. `MouseDaemonManager`) caused them to race for each event — about
     * half the time the wrong collector won and the UI/sync state never
     * reflected the write. Switched to `MutableSharedFlow<Unit>` so all
     * subscribers see every signal.
     *
     * `Unit` is sufficient: subscribers re-read the full table on each tick;
     * coalescing rapid bursts via DROP_OLDEST is therefore safe and desirable.
     */
    private val changeSignal =
        MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override fun getAllSyncRuntimeInfosFlow(): Flow<List<SyncRuntimeInfo>> =
        changeSignal
            .onSubscription { emit(Unit) }
            .map { getAllSyncRuntimeInfos() }
            .flowOn(ioDispatcher)

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
                writeMutex.withLock {
                    database.transactionWithResult {
                        updateAction(syncRuntimeInfo)
                    }
                }
            if (change) {
                changeSignal.tryEmit(Unit)
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
                writeMutex.withLock {
                    database.transactionWithResult {
                        syncRuntimeInfoDatabaseQueries.deleteSyncRuntimeInfo(appInstanceId)
                        syncRuntimeInfoDatabaseQueries.change().executeAsOne() > 0
                    }
                }
            if (deleted) {
                changeSignal.tryEmit(Unit)
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
                writeMutex.withLock {
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
                                            existing.connectNetworkPrefixLength?.toLong() !=
                                            connectNetworkPrefixLength ||
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
                                if (connectInfo !=
                                    null
                                ) {
                                    SyncState.CONNECTED.toLong()
                                } else {
                                    SyncState.DISCONNECTED.toLong()
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
                }

            if (changed) {
                changeSignal.tryEmit(Unit)
            }
        }
    }
}
