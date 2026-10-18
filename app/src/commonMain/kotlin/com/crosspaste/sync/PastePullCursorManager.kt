package com.crosspaste.sync

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.paste.PasteData
import com.crosspaste.utils.StripedMutex
import io.ktor.util.collections.ConcurrentMap

/** Tracks the peer's database IDs, never our receive/display timestamps. */
class PastePullCursorManager(
    private val pasteDao: PasteDao,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
) {
    private val remoteIds: MutableMap<String, Long> = ConcurrentMap()
    private val deviceMutex = StripedMutex()

    suspend fun init() {
        pasteDao.getRemotePasteCursors().forEach { (peer, id) ->
            deviceMutex.withLock(peer) {
                remoteIds[peer] = maxOf(remoteIds[peer] ?: 0L, id)
            }
        }
    }

    fun getRemoteId(appInstanceId: String): Long? = remoteIds[appInstanceId]

    suspend fun getQueryAfterId(appInstanceId: String): Long? =
        deviceMutex.withLock(appInstanceId) {
            val cursor = refreshCursor(appInstanceId) ?: return@withLock null
            val oldestPending = pasteDao.getPendingPasteReceives(appInstanceId).keys.minOrNull()
            oldestPending?.let { minOf(cursor, it - 1) } ?: cursor
        }

    /** Reserve the entire selected batch before ingest, including items after a possible failure. */
    suspend fun reserve(
        appInstanceId: String,
        remotePasteIds: List<Long>,
    ) {
        deviceMutex.withLock(appInstanceId) {
            if (syncRuntimeInfoDao.getSyncRuntimeInfo(appInstanceId) == null) return@withLock
            val cursor = remoteIds[appInstanceId] ?: 0L
            val pending = pasteDao.getPendingPasteReceives(appInstanceId)
            val ids = remotePasteIds.filter { it > 0 && (it > cursor || it in pending) }
            pasteDao.reservePasteReceives(appInstanceId, ids)
        }
    }

    suspend fun bindFile(
        appInstanceId: String,
        remotePasteId: Long,
        localPasteId: Long,
    ) {
        deviceMutex.withLock(appInstanceId) {
            pasteDao.bindPasteReceive(appInstanceId, remotePasteId, localPasteId)
        }
    }

    suspend fun settle(
        appInstanceId: String,
        remotePasteId: Long,
    ): Boolean =
        deviceMutex.withLock(appInstanceId) {
            if (remotePasteId <= 0 || syncRuntimeInfoDao.getSyncRuntimeInfo(appInstanceId) == null) {
                return@withLock false
            }
            pasteDao.completePasteReceive(appInstanceId, remotePasteId)
            remoteIds[appInstanceId] = maxOf(remoteIds[appInstanceId] ?: 0L, remotePasteId)
            true
        }

    suspend fun settleFile(
        appInstanceId: String,
        localPasteId: Long,
    ) {
        val remoteId =
            pasteDao
                .getPendingPasteReceives(appInstanceId)
                .entries
                .firstOrNull { it.value == localPasteId }
                ?.key ?: return
        settle(appInstanceId, remoteId)
    }

    /** Skip completed/deleted receipts and live downloads before any side effects. */
    suspend fun selectUnreceived(
        appInstanceId: String,
        pastes: List<PasteData>,
    ): List<PasteData> {
        val selected =
            deviceMutex.withLock(appInstanceId) {
                pasteDao.getUnreceivedPasteIds(appInstanceId, pastes.map { it.id })
            }
        return pastes.distinctBy { it.id }.filter { it.id in selected }
    }

    private suspend fun refreshCursor(appInstanceId: String): Long? {
        val cursor = pasteDao.getRemotePasteCursor(appInstanceId)
        if (cursor == null) remoteIds.remove(appInstanceId) else remoteIds[appInstanceId] = cursor
        return cursor
    }

    suspend fun shouldSkipHistory(pasteData: PasteData): Boolean =
        selectUnreceived(pasteData.appInstanceId, listOf(pasteData)).isEmpty()

    suspend fun hasActiveReceive(pasteData: PasteData): Boolean {
        val localId = pasteDao.getPendingPasteReceives(pasteData.appInstanceId)[pasteData.id] ?: return false
        return pasteDao.getNoDeletePasteData(localId) != null
    }

    suspend fun removeDevice(appInstanceId: String) {
        deviceMutex.withLock(appInstanceId) {
            pasteDao.deleteRemotePasteCursor(appInstanceId)
            pasteDao.deletePastePullCursor(appInstanceId)
            remoteIds.remove(appInstanceId)
        }
    }
}
