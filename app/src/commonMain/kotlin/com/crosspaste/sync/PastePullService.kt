package com.crosspaste.sync

import com.crosspaste.db.sync.SyncState
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.routing.bindAuthenticatedRemoteIdentity
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteboardService
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging

class PastePullService(
    private val pastePullCursorManager: PastePullCursorManager,
    private val pasteboardService: PasteboardService,
    private val pullClientApi: PullClientApi,
    private val syncManager: SyncManager,
) {

    companion object {
        // The peer caps /pull/pasteBatch at 50 items
        const val MAX_PULL_BATCH_LIMIT = 50L
    }

    private val logger = KotlinLogging.logger {}

    suspend fun init() {
        pastePullCursorManager.init()
    }

    fun getMaxCreateTime(appInstanceId: String): Long? = pastePullCursorManager.getMaxCreateTime(appInstanceId)

    suspend fun updateMaxCreateTime(
        appInstanceId: String,
        createTime: Long,
    ) {
        pastePullCursorManager.updateMaxCreateTime(appInstanceId, createTime)
    }

    suspend fun pullAllDevices(limit: Long = 10L) {
        val allPasteData =
            syncManager
                .getSyncHandlers()
                .filter { (appInstanceId, handler) -> canPullFrom(appInstanceId, handler) }
                .keys
                .flatMap { appInstanceId -> pullBatch(appInstanceId, limit) }

        writePulled(allPasteData)
    }

    /**
     * Catch up on [appInstanceId]'s pastes newer than its cursor. Never runs
     * without a cursor: a peer this device has never received from would
     * otherwise hand over its whole recent history.
     */
    suspend fun pullDevice(
        appInstanceId: String,
        limit: Long = MAX_PULL_BATCH_LIMIT,
    ) {
        val handler = syncManager.getSyncHandler(appInstanceId) ?: return
        if (!canPullFrom(appInstanceId, handler)) return
        if (getMaxCreateTime(appInstanceId) == null) {
            logger.debug { "Skip pull from $appInstanceId: nothing received from it yet" }
            return
        }
        writePulled(pullBatch(appInstanceId, limit))
    }

    /**
     * Fetch the pastes newer than the cursor of [appInstanceId]. The cursor is
     * not advanced here; see [writePulled].
     */
    suspend fun pullBatch(
        appInstanceId: String,
        limit: Long = 10L,
    ): List<PasteData> {
        val handler =
            syncManager.getSyncHandler(appInstanceId) ?: run {
                logger.debug { "No sync handler for $appInstanceId" }
                return emptyList()
            }

        val hostAddress =
            handler.getConnectHostAddress() ?: run {
                logger.debug { "No connect address for $appInstanceId" }
                return emptyList()
            }

        val syncRuntimeInfo = handler.currentSyncRuntimeInfo
        val hostAndPort = HostAndPort(hostAddress, syncRuntimeInfo.port)
        val createTime = getMaxCreateTime(appInstanceId)

        val result =
            pullClientApi.pullPasteBatch(
                targetAppInstanceId = appInstanceId,
                createTime = createTime,
                limit = limit,
            ) {
                buildUrl(hostAndPort)
            }

        return if (result is SuccessResult) {
            // The peer serves only its own pastes; bind them to it like every other receive path
            val pasteDataList =
                result
                    .getResult<List<PasteData>>()
                    .map { it.bindAuthenticatedRemoteIdentity(appInstanceId) }
                    .toOldestFirst(createTime)
            logger.debug { "Pulled ${pasteDataList.size} paste(s) from $appInstanceId" }
            pasteDataList
        } else {
            logger.warn { "Failed to pull paste batch from $appInstanceId: $result" }
            emptyList()
        }
    }

    /**
     * The peer answers newest first, and createTime is not on the wire: decoding
     * stamps every paste with the local receive time. Restamp them oldest first,
     * strictly increasing up to now (and strictly newer than [previousMaxCreateTime]),
     * so history keeps the peer's order and timestamps are monotonic.
     */
    private fun List<PasteData>.toOldestFirst(previousMaxCreateTime: Long? = null): List<PasteData> {
        if (isEmpty()) return emptyList()
        val now = nowEpochMilliseconds()
        val baseTime = maxOf(now, (previousMaxCreateTime ?: 0L) + size)
        return asReversed().mapIndexed { index, pasteData ->
            pasteData.copy(createTime = baseTime - (lastIndex - index))
        }
    }

    private fun canPullFrom(
        appInstanceId: String,
        handler: SyncHandler,
    ): Boolean {
        val syncRuntimeInfo = handler.currentSyncRuntimeInfo
        return when {
            syncRuntimeInfo.connectState != SyncState.CONNECTED -> false
            !syncRuntimeInfo.allowReceive -> {
                logger.debug { "Skip pull from $appInstanceId: allowReceive is false" }
                false
            }
            // Same protocol gate as pushing to the peer
            handler.currentVersionRelation != VersionRelation.EQUAL_TO -> {
                logger.debug { "Skip pull from $appInstanceId: incompatible version" }
                false
            }
            else -> true
        }
    }

    /**
     * Stores the pulled pastes in createTime order (without writing to the active
     * system clipboard), then advances each device's cursor. A failed write leaves the
     * cursors untouched so a later pull fetches the batch again.
     */
    private suspend fun writePulled(
        pasteDataList: List<PasteData>,
        writeToPasteboard: Boolean = false,
    ) {
        if (pasteDataList.isEmpty()) {
            return
        }

        val sorted = pasteDataList.sortedBy { it.createTime }

        pasteboardService
            .tryWriteRemotePasteboardList(sorted, writeToPasteboard)
            .onSuccess {
                sorted
                    .groupBy { it.appInstanceId }
                    .forEach { (appInstanceId, pastes) ->
                        updateMaxCreateTime(appInstanceId, pastes.maxOf { it.createTime })
                    }
                logger.info { "Pulled and wrote ${sorted.size} paste(s)" }
            }.onFailure { e ->
                logger.warn(e) { "Stopped writing pulled pastes after a release failure" }
            }
    }
}
