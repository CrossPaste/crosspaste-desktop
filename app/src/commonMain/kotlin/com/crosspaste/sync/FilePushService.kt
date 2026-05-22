package com.crosspaste.sync

import com.crosspaste.dto.push.PushCompleteResponse
import com.crosspaste.dto.push.PushPrepareResponse
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.PushClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesIndex
import com.crosspaste.presist.buildFilesIndex
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CancellationException

/**
 * Client-side orchestrator for the file push protocol. Pairs with the
 * M1+M2 server endpoints in [com.crosspaste.net.routing.PushRouting]
 * (`/sync/file/push`, `/sync/paste/push/complete`, `/sync/icon/push/{source}`)
 * plus the push-mode branch of `/sync/paste`.
 *
 * Lifecycle:
 *   1. preparePush → server allocates a [com.crosspaste.sync.PushSession]
 *      with `(pasteId, chunkCount, chunkSize, sessionToken)`.
 *   2. Build a local [com.crosspaste.presist.FilesIndex] with the same
 *      `chunkSize`; assert chunk-count parity (mismatch is a protocol bug).
 *   3. Fan out per-chunk uploads via [PasteSyncProcessManager.runTask]
 *      (re-uses the existing rate limiter; concurrency cap is at the
 *      process-manager layer).
 *   4. completePush — server returns `missingChunks`. Retry only the missing
 *      ones; bounded by [MAX_COMPLETE_ROUNDS] so a bad chunk doesn't loop forever.
 *   5. Optional fire-and-forget icon push when `needIcon=true`. The pull-icon
 *      task on the receiver remains the fallback, so a failed icon push does
 *      NOT downgrade the overall push result.
 *
 * Any failure (prepare, chunks, complete) returns a [FailureResult] so the
 * caller (SyncPasteTaskExecutor) can fall back to pull-mode metadata send.
 */
class FilePushService(
    private val pasteSyncProcessManager: PasteSyncProcessManager<Long>,
    private val pushClientApi: PushClientApi,
    private val userDataPathProvider: UserDataPathProvider,
    /**
     * Indirection so tests can inject a deterministic FilesIndex without
     * needing real files on disk. Production wires this to the canonical
     * [buildFilesIndex] from `presist/FilesIndexFactory.kt`.
     */
    private val filesIndexFactory: (PasteData, UserDataPathProvider, Long) -> FilesIndex =
        ::buildFilesIndex,
    /**
     * Indirection for chunk-bytes reading so the happy-path test doesn't need
     * real files. Production wires to [FileUtils.readFilesChunkToByteArray].
     */
    private val chunkReader: (com.crosspaste.presist.FilesChunk) -> ByteArray =
        { chunk -> fileUtils.readFilesChunkToByteArray(chunk) },
) {

    companion object {
        /**
         * Bound on the prepare → upload → complete retry loop. One initial
         * round + one retry of any reported `missingChunks`. Two is enough to
         * cover transient packet loss without livelocking on a poisoned chunk
         * (which would also fail the second time → caller falls back to pull).
         */
        const val MAX_COMPLETE_ROUNDS: Int = 2

        private val logger = KotlinLogging.logger {}

        private val fileUtils: FileUtils = getFileUtils()
    }

    /**
     * Pushes [pasteData]'s file payload to a peer at [toUrl]. Returns
     * [SuccessResult] only when complete; any failure path returns a
     * [FailureResult] so the caller can fall through to pull-mode metadata
     * send. The push is `targetAppInstanceId`-scoped — the encryption layer
     * picks the right SecureMessageProcessor based on this header.
     */
    @Suppress("ReturnCount")
    suspend fun pushFiles(
        pasteData: PasteData,
        targetAppInstanceId: String,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult {
        try {
            // 1. prepare
            val prepareResult = pushClientApi.preparePush(pasteData, targetAppInstanceId, toUrl)
            if (prepareResult !is SuccessResult) {
                logger.warn { "push preparePush failed for target=$targetAppInstanceId: $prepareResult" }
                return prepareResult
            }
            val prepare = prepareResult.getResult<PushPrepareResponse>()

            // 2. build local index with the server-provided chunkSize
            val filesIndex = filesIndexFactory(pasteData, userDataPathProvider, prepare.chunkSize)
            val localChunkCount = filesIndex.getChunkCount()
            if (localChunkCount != prepare.chunkCount) {
                logger.error {
                    "push chunkCount mismatch for target=$targetAppInstanceId: " +
                        "local=$localChunkCount server=${prepare.chunkCount} chunkSize=${prepare.chunkSize}"
                }
                return createFailureResult(
                    StandardErrorCode.PUSH_CHUNK_COUNT_MISMATCH,
                    "chunkCount mismatch local=$localChunkCount server=${prepare.chunkCount}",
                )
            }

            // 3. + 4. upload chunks, retrying only missing ones up to MAX_COMPLETE_ROUNDS.
            var chunksToUpload: List<Int> = (0 until prepare.chunkCount).toList()
            var round = 0
            while (round < MAX_COMPLETE_ROUNDS) {
                round += 1
                val uploadFailures =
                    uploadChunks(
                        prepare = prepare,
                        targetAppInstanceId = targetAppInstanceId,
                        pasteDataId = pasteData.id,
                        chunkIndices = chunksToUpload,
                        filesIndex = filesIndex,
                        toUrl = toUrl,
                    )
                if (uploadFailures.isNotEmpty()) {
                    logger.warn {
                        "push round=$round had ${uploadFailures.size} upload failures " +
                            "for target=$targetAppInstanceId pasteId=${prepare.pasteId}"
                    }
                    return uploadFailures.values.first()
                }

                val completeResult =
                    pushClientApi.completePush(
                        pasteId = prepare.pasteId,
                        sessionToken = prepare.sessionToken,
                        targetAppInstanceId = targetAppInstanceId,
                        toUrl = toUrl,
                    )
                if (completeResult !is SuccessResult) {
                    logger.warn {
                        "push completePush failed for target=$targetAppInstanceId " +
                            "pasteId=${prepare.pasteId}: $completeResult"
                    }
                    return completeResult
                }
                val complete = completeResult.getResult<PushCompleteResponse>()
                if (complete.isComplete) {
                    maybePushIcon(prepare, pasteData, targetAppInstanceId, toUrl)
                    return SuccessResult()
                }
                logger.info {
                    "push pasteId=${prepare.pasteId} missing=${complete.missingChunks.size} " +
                        "after round=$round, retrying"
                }
                chunksToUpload = complete.missingChunks
            }

            return createFailureResult(
                StandardErrorCode.PUSH_COMPLETE_FAIL,
                "pasteId=${prepare.pasteId} still has missing chunks after $MAX_COMPLETE_ROUNDS rounds",
            )
        } finally {
            pasteSyncProcessManager.cleanProcess(pasteData.id)
        }
    }

    /**
     * Uploads [chunkIndices] concurrently through [PasteSyncProcessManager.runTask]
     * (shared semaphore, per-task timeout). Returns a map of `chunkIndex →
     * FailureResult` for any failed chunks; empty means all succeeded.
     */
    private suspend fun uploadChunks(
        prepare: PushPrepareResponse,
        targetAppInstanceId: String,
        pasteDataId: Long,
        chunkIndices: List<Int>,
        filesIndex: FilesIndex,
        toUrl: URLBuilder.() -> Unit,
    ): Map<Int, FailureResult> {
        if (chunkIndices.isEmpty()) return emptyMap()

        val tasks: List<suspend () -> Pair<Int, ClientApiResult>> =
            chunkIndices.map { chunkIndex ->
                {
                    try {
                        val chunk = filesIndex.getChunk(chunkIndex)
                        if (chunk == null) {
                            Pair(
                                chunkIndex,
                                createFailureResult(
                                    StandardErrorCode.PUSH_CHUNK_UPLOAD_FAIL,
                                    "chunkIndex $chunkIndex out of range",
                                ),
                            )
                        } else {
                            val bytes = chunkReader(chunk)
                            val result =
                                pushClientApi.pushChunk(
                                    pasteId = prepare.pasteId,
                                    chunkIndex = chunkIndex,
                                    sessionToken = prepare.sessionToken,
                                    targetAppInstanceId = targetAppInstanceId,
                                    chunkBytes = bytes,
                                    toUrl = toUrl,
                                )
                            Pair(chunkIndex, result)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Pair(
                            chunkIndex,
                            createFailureResult(
                                StandardErrorCode.PUSH_CHUNK_UPLOAD_FAIL,
                                "push chunk $chunkIndex failed: ${e.message}",
                            ),
                        )
                    }
                }
            }

        val results = pasteSyncProcessManager.runTask(pasteDataId, tasks)
        return results
            .filter { it.second !is SuccessResult }
            .associate { (idx, result) ->
                idx to
                    when (result) {
                        is FailureResult -> result
                        else ->
                            createFailureResult(
                                StandardErrorCode.PUSH_CHUNK_UPLOAD_FAIL,
                                "push chunk $idx returned $result",
                            )
                    }
            }
    }

    /**
     * Fire-and-forget icon push when the peer signalled `needIcon=true`. The
     * receiver's pull-icon task is the durable fallback (per M1+M2 design), so
     * any failure here is logged and dropped — pushing icons must NEVER fail
     * the file push it accompanies.
     */
    private suspend fun maybePushIcon(
        prepare: PushPrepareResponse,
        pasteData: PasteData,
        targetAppInstanceId: String,
        toUrl: URLBuilder.() -> Unit,
    ) {
        if (!prepare.needIcon) return
        val source = pasteData.source ?: return
        runCatching {
            val iconPath = userDataPathProvider.resolveIconPath(pasteData.appInstanceId, source)
            if (!fileUtils.existFile(iconPath)) {
                logger.debug { "push icon skipped: no local icon for source=$source" }
                return
            }
            val bytes =
                runCatching { fileUtils.fileSystem.read(iconPath) { readByteArray() } }
                    .getOrNull() ?: return
            val result =
                pushClientApi.pushIcon(
                    source = source,
                    targetAppInstanceId = targetAppInstanceId,
                    iconBytes = bytes,
                    toUrl = toUrl,
                )
            if (result !is SuccessResult) {
                logger.info {
                    "push icon failed for source=$source target=$targetAppInstanceId: $result (falling back to pull-icon)"
                }
            }
        }.onFailure { e ->
            logger.warn(e) { "push icon errored for source=${pasteData.source} target=$targetAppInstanceId" }
        }
    }
}
