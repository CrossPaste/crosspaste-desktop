package com.crosspaste.sync

import com.crosspaste.db.sync.SyncState
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.routing.bindAuthenticatedRemoteIdentity
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteboardService
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.StripedMutex
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap

class PastePullService(
    private val pastePullCursorManager: PastePullCursorManager,
    private val pasteboardService: PasteboardService,
    private val pullClientApi: PullClientApi,
    private val syncManager: SyncManager,
) {
    companion object {
        const val MAX_PULL_BATCH_LIMIT = 50L
    }

    private val logger = KotlinLogging.logger {}
    private val pullMutex = StripedMutex()

    // Re-probe when the peer upgrades or moves address/port; never downgrade on a transport/auth failure.
    private val legacyPeers: MutableMap<String, String> = ConcurrentMap()

    suspend fun init() = pastePullCursorManager.init()

    suspend fun pullAllDevices(limit: Long = 10L) {
        val pastes =
            syncManager.getSyncHandlers().keys.flatMap { peer ->
                pullMutex.withLock(peer) {
                    pullBatch(peer, limit).also { batch ->
                        pastePullCursorManager.reserve(peer, batch.map { it.id })
                    }
                }
            }
        writePulled(pastes.sortedBy { it.createTime })
    }

    /** One bounded recent window per reconnect. Older overflow is intentionally not drained. */
    suspend fun pullDevice(
        appInstanceId: String,
        limit: Long = MAX_PULL_BATCH_LIMIT,
    ) {
        pullMutex.withLock(appInstanceId) {
            val handler = syncManager.getSyncHandler(appInstanceId) ?: return@withLock
            if (!canPullFrom(handler)) return@withLock
            val pastes = pullBatch(appInstanceId, limit)
            if (pastes.isEmpty()) return@withLock
            pastePullCursorManager.reserve(appInstanceId, pastes.map { it.id })
            writePulled(pastes)
            // Ingest/finalize advances the receipt cursor, never fetching or merely scheduling a download.
        }
    }

    private suspend fun writePulled(pastes: List<PasteData>) {
        if (pastes.isEmpty()) return
        val writeClipboard = pasteboardService.configManager.getCurrentConfig().enableAutoPullWriteClipboard
        pasteboardService
            .tryWriteRemotePasteboardList(pastes, writeClipboard)
            .onFailure { logger.warn(it) { "Stopped writing pulled pastes" } }
    }

    suspend fun pullBatch(
        appInstanceId: String,
        limit: Long = 10L,
    ): List<PasteData> {
        val handler = syncManager.getSyncHandler(appInstanceId) ?: return emptyList()
        if (!canPullFrom(handler)) return emptyList()
        val afterId = pastePullCursorManager.getQueryAfterId(appInstanceId) ?: return emptyList()
        val host = handler.getConnectHostAddress() ?: return emptyList()
        val info = handler.currentSyncRuntimeInfo
        val address = HostAndPort(host, info.port)
        val peerVersion = "${info.appVersion}:$host:${info.port}"
        val boundedLimit = limit.coerceIn(1L, MAX_PULL_BATCH_LIMIT)
        var result =
            if (legacyPeers[appInstanceId] == peerVersion) {
                pullClientApi.pullPasteBatch(appInstanceId, null, boundedLimit) { buildUrl(address) }
            } else {
                pullClientApi.pullPasteBatchById(appInstanceId, afterId, boundedLimit) { buildUrl(address) }
            }
        if (result is FailureResult &&
            result.exception.match(StandardErrorCode.NOT_FOUND_API) &&
            legacyPeers[appInstanceId] != peerVersion
        ) {
            legacyPeers[appInstanceId] = peerVersion
            result = pullClientApi.pullPasteBatch(appInstanceId, null, boundedLimit) { buildUrl(address) }
        }
        if (result !is SuccessResult) {
            logger.warn { "Failed to pull from $appInstanceId: $result" }
            return emptyList()
        }
        val received =
            result
                .getResult<List<PasteData>>()
                .take(boundedLimit.toInt())
                .map { it.bindAuthenticatedRemoteIdentity(appInstanceId) }
        val selected = pastePullCursorManager.selectUnreceived(appInstanceId, received).sortedBy { it.id }
        val now = nowEpochMilliseconds()
        return selected.mapIndexed { index, paste -> paste.copy(createTime = now - (selected.lastIndex - index)) }
    }

    private fun canPullFrom(handler: SyncHandler): Boolean =
        handler.currentSyncRuntimeInfo.let {
            it.connectState == SyncState.CONNECTED &&
                it.allowReceive &&
                handler.currentVersionRelation == VersionRelation.EQUAL_TO
        }
}
