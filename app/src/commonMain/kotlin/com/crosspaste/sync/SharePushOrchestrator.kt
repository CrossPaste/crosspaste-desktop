package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.PasteClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.PasteData
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.URLBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Top-level entry point for "I must complete this sync now" scenarios — i.e.
 * the iOS Share Extension and any other short-lived sender. Fans out to all
 * peers currently in CONNECTED state and pushes (file-type) or sends metadata
 * (text / url / etc.) directly, bypassing the SyncTask pipeline.
 *
 * Why not run this through SyncPasteTaskExecutor + TaskExecutor like the
 * clipboard-capture flow does? Because push is a fundamentally different sync
 * model: synchronous, in-process, no retry across process boundaries. Burying
 * it inside the task executor leaked task-system semantics (DB-persisted
 * retries, NearbyDeviceManager wiring, SyncManager startup) into callers that
 * cannot tolerate them.
 *
 * This service is intentionally state-free — it reads SyncRuntimeInfo
 * snapshots from the DB and reuses the existing client APIs. It does NOT
 * start SyncManager, does NOT depend on in-memory SyncHandler state, and does
 * NOT write back to SyncRuntimeInfo. Safe to call from a sandboxed extension
 * process without polluting the main app's state.
 */
class SharePushOrchestrator(
    private val pasteClientApi: PasteClientApi,
    private val filePushService: FilePushService,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun pushToConnectedPeers(pasteData: PasteData): SharePushResult =
        coroutineScope {
            val targets = eligibleTargets()
            if (targets.isEmpty()) {
                logger.info { "pushToConnectedPeers: no CONNECTED peers; pasteId=${pasteData.id}" }
                return@coroutineScope SharePushResult(emptyMap())
            }
            val perTarget =
                targets
                    .map { target -> async { target.appInstanceId to pushOne(pasteData, target) } }
                    .awaitAll()
                    .toMap()
            SharePushResult(perTarget)
        }

    /**
     * `connectState == CONNECTED` implies `EQUAL_TO` — see
     * `GeneralSyncHandler.handleValueChange`: a version-mismatch resolve
     * transitions the handler to `INCOMPATIBLE`, not `CONNECTED`. So filtering
     * on `CONNECTED` + `allowSend` is sufficient and works off persisted DB
     * state alone, without needing in-memory `SyncHandler` / `versionRelation`.
     */
    private suspend fun eligibleTargets(): List<SyncRuntimeInfo> =
        syncRuntimeInfoDao
            .getAllSyncRuntimeInfos()
            .filter { it.connectState == SyncState.CONNECTED && it.allowSend }

    private suspend fun pushOne(
        pasteData: PasteData,
        target: SyncRuntimeInfo,
    ): ClientApiResult {
        val host =
            target.connectHostAddress
                ?: run {
                    logger.warn {
                        "pushToConnectedPeers: skipping ${target.appInstanceId} — no connectHostAddress"
                    }
                    return createFailureResult(
                        StandardErrorCode.CANT_GET_SYNC_ADDRESS,
                        "no connect host address for ${target.appInstanceId}",
                    )
                }
        val toUrl: URLBuilder.() -> Unit = { buildUrl(HostAndPort(host, target.port)) }
        return if (pasteData.isFileType()) {
            filePushService.pushFiles(pasteData, target.appInstanceId, toUrl)
        } else {
            pasteClientApi.sendPaste(pasteData, target.appInstanceId, toUrl)
        }
    }
}

data class SharePushResult(
    val perTarget: Map<String, ClientApiResult>,
) {
    val allSucceeded: Boolean = perTarget.isNotEmpty() && perTarget.values.all { it is SuccessResult }
    val anySucceeded: Boolean = perTarget.values.any { it is SuccessResult }
}
