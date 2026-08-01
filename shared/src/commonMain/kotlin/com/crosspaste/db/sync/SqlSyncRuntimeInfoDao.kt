package com.crosspaste.db.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.crosspaste.Database
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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
     * Broadcast semantics: every collector independently observes the query via
     * SQLDelight's listener mechanism, so each one receives the current snapshot
     * on subscription and every subsequent change — concurrent collectors never
     * compete for notifications (R2-02-005). [distinctUntilChanged] suppresses
     * re-emissions from table notifications that did not change the result set.
     *
     * This is a latest-wins *state* flow, not an event stream: rapid successive
     * writes may be conflated into a single emission of the newest snapshot, so
     * consumers must not rely on observing every intermediate state.
     */
    override fun getAllSyncRuntimeInfosFlow(): Flow<List<SyncRuntimeInfo>> =
        syncRuntimeInfoDatabaseQueries
            .getAllSyncRuntimeInfos(SyncRuntimeInfo::mapper)
            .asFlow()
            .mapToList(ioDispatcher)
            .distinctUntilChanged()

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
            writeMutex.withLock {
                database.transaction {
                    syncRuntimeInfoDatabaseQueries.deleteSyncRuntimeInfo(appInstanceId)
                }
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
                        // Recency-ordered, capacity-capped merge (LRU) instead of an
                        // unbounded union — bounds ghost-address accumulation (#4499).
                        val hostInfoList =
                            HostInfo.mergeRecent(
                                existing = existing.hostInfoList,
                                incoming = syncInfo.endpointInfo.hostInfoList,
                                now = now,
                            )

                        // When this update carries no connectInfo (e.g. an mDNS
                        // re-advertisement), preserve the existing connect address /
                        // prefix instead of nulling them. The connectState column is
                        // already preserved via a CASE on the -1 sentinel; the address
                        // must be preserved too, otherwise a peer's IP-change broadcast
                        // wipes connectHostAddress and forces a visible disconnect
                        // (#4499 weakness ①).
                        val connectNetworkPrefixLength: Long? =
                            connectInfo?.networkPrefixLength?.toLong()
                                ?: existing.connectNetworkPrefixLength?.toLong()
                        val connectHostAddress: String? =
                            connectInfo?.hostAddress ?: existing.connectHostAddress
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

                        // Stamp recency and apply the capacity cap on first insert too,
                        // so a peer can't seed an unbounded address list.
                        val hostInfoList =
                            HostInfo.mergeRecent(
                                existing = emptyList(),
                                incoming = syncInfo.endpointInfo.hostInfoList,
                                now = now,
                            )
                        val hostInfoArrayJson = jsonUtils.JSON.encodeToString(hostInfoList)
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
        }
    }
}
