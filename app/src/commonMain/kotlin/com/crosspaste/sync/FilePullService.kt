package com.crosspaste.sync

import com.crosspaste.dto.pull.PullFileRequest
import com.crosspaste.dto.pull.WsPullFileRequest
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.net.ws.WsEnvelope
import com.crosspaste.net.ws.WsMessageType
import com.crosspaste.net.ws.WsPendingRequests
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.URLBuilder
import io.ktor.utils.io.ByteReadChannel
import okio.Path.Companion.toPath

sealed class FilePullResult {
    data class Success(
        val renameMap: Map<String, String>,
    ) : FilePullResult()

    data class Failure(
        val failedChunks: Map<Int, FailureResult>,
        val pullChunks: IntArray,
    ) : FilePullResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Failure) return false
            return failedChunks == other.failedChunks && pullChunks.contentEquals(other.pullChunks)
        }

        override fun hashCode(): Int {
            var result = failedChunks.hashCode()
            result = 31 * result + pullChunks.contentHashCode()
            return result
        }
    }

    data object Empty : FilePullResult()

    data class NoSyncHandler(
        val appInstanceId: String,
    ) : FilePullResult()

    data class NoSyncAddress(
        val appInstanceId: String,
    ) : FilePullResult()
}

class FilePullService(
    private val pullClientApi: PullClientApi,
    private val userDataPathProvider: UserDataPathProvider,
    private val pasteSyncProcessManager: PasteSyncProcessManager<Long>,
    private val syncManager: SyncManager,
    private val wsPendingRequests: WsPendingRequests,
    private val wsSessionManager: WsSessionManager,
) {

    companion object {

        private val logger = KotlinLogging.logger {}

        const val CHUNK_SIZE: Long = 1024 * 1024 // 1MB

        private val dateUtils: DateUtils = getDateUtils()

        private val fileUtils: FileUtils = getFileUtils()

        private val json = getJsonUtils().JSON
    }

    /**
     * Pull files from a remote device in chunks.
     *
     * @param pullChunks tracks per-chunk download status (0 = pending, 1 = done).
     *   Empty on first attempt; on retry, carries forward the state from the previous attempt
     *   so only failed chunks are re-downloaded.
     *
     * Note: `resolveConflicts` is set to `!isRetry` because filename conflict resolution
     * (e.g. renaming "a.txt" to "a (1).txt") must only happen on the first pull.
     * Retries must reuse the same resolved paths to keep the `FilesIndex` consistent
     * with already-downloaded chunks.
     */
    suspend fun pullFiles(
        appInstanceId: String,
        pasteId: Long,
        createTime: Long,
        remotePasteId: Long,
        pasteFiles: PasteFiles,
        pullChunks: IntArray,
    ): FilePullResult {
        val syncHandler =
            syncManager.getSyncHandlers()[appInstanceId]
                ?: return FilePullResult.NoSyncHandler(appInstanceId)

        // Extension devices (e.g. Chrome) have no HTTP server — use WebSocket whole-file pull
        if (syncHandler.getSyncPlatform().isExtension()) {
            return pullFilesViaWs(appInstanceId, pasteId, createTime, pasteFiles)
        }

        return pullFilesViaHttp(
            appInstanceId,
            pasteId,
            createTime,
            remotePasteId,
            pasteFiles,
            pullChunks,
            syncHandler,
        )
    }

    /**
     * Pull files from a Chrome extension via WebSocket using whole-file mode.
     * Chrome files are always ≤ 1MB, so no chunking is needed.
     * Files are requested by paste hash + file name.
     */
    private suspend fun pullFilesViaWs(
        appInstanceId: String,
        pasteId: Long,
        createTime: Long,
        pasteFiles: PasteFiles,
    ): FilePullResult {
        val dateString =
            dateUtils.getYMD(
                dateUtils.epochMillisecondsToLocalDateTime(createTime),
            )

        // Resolve target paths and create empty placeholder files.
        // Pass null for filesIndexBuilder — we don't need chunk indexing in whole-file mode.
        val renameMap =
            userDataPathProvider.resolve(
                appInstanceId,
                dateString,
                pasteId,
                pasteFiles,
                true,
                null,
                resolveConflicts = true,
            )

        // Build a map from original file name → actual disk path (accounting for renames)
        val filePaths = pasteFiles.getFilePaths(userDataPathProvider)
        if (filePaths.isEmpty()) {
            logger.warn { "No files to pull via WS for pasteId $pasteId" }
            return FilePullResult.Empty
        }

        // filePaths are aligned with relativePathList and use original names.
        // If a rename happened, the actual file on disk has the renamed name in the same parent dir.
        val targetPaths =
            pasteFiles.relativePathList.zip(filePaths).map { (originalName, originalPath) ->
                val renamedName = renameMap[originalName]
                if (renamedName != null) {
                    originalPath.parent!! / renamedName
                } else {
                    originalPath
                }
            }

        val hash = (pasteFiles as? com.crosspaste.paste.item.PasteItem)?.hash ?: ""
        if (hash.isEmpty()) {
            logger.error { "Cannot pull files via WS: paste hash is empty for pasteId $pasteId" }
            return FilePullResult.Failure(
                mapOf(
                    0 to
                        createFailureResult(
                            StandardErrorCode.PULL_FILE_TASK_FAIL,
                            "Paste hash is empty",
                        ) as FailureResult,
                ),
                intArrayOf(),
            )
        }

        val failedFiles = mutableMapOf<Int, FailureResult>()

        // Request each file individually by hash + fileName.
        // relativePathList may contain bind-transformed paths like "appInstanceId/date/id/file.png",
        // but Chrome's BlobStore stores by the original file name only (e.g. "file.png").
        pasteFiles.relativePathList.forEachIndexed { index, relativePath ->
            val requestFileName = relativePath.toPath().name
            val targetPath = targetPaths[index]

            runCatching {
                val request =
                    WsPullFileRequest(
                        hash = hash,
                        fileName = requestFileName,
                    )

                val requestEnvelope =
                    WsEnvelope(
                        type = WsMessageType.FILE_PULL_REQUEST,
                        payload = json.encodeToString(request).encodeToByteArray(),
                    )

                val response =
                    wsPendingRequests.request(
                        wsSessionManager,
                        appInstanceId,
                        requestEnvelope,
                    )

                if (response.type == WsMessageType.ERROR) {
                    val errorMsg = response.payload.decodeToString()
                    throw IllegalStateException("File pull error: $errorMsg")
                }

                // Write the received bytes directly to the target file path
                fileUtils.writeFile(targetPath) { sink ->
                    sink.write(response.payload)
                }

                logger.debug { "WS file pull success: $requestFileName → $targetPath (${response.payload.size} bytes)" }
            }.onFailure { e ->
                logger.error(e) { "WS file pull failed for $requestFileName from $appInstanceId" }
                failedFiles[index] =
                    createFailureResult(
                        StandardErrorCode.PULL_FILE_CHUNK_TASK_FAIL,
                        "WS file pull failed: ${e.message}",
                    ) as FailureResult
            }
        }

        return if (failedFiles.isEmpty()) {
            FilePullResult.Success(renameMap)
        } else {
            // For WS whole-file mode, pullChunks is not meaningful (no chunk system),
            // but return an empty array to satisfy the Failure contract.
            FilePullResult.Failure(failedFiles, intArrayOf())
        }
    }

    private suspend fun pullFilesViaHttp(
        appInstanceId: String,
        pasteId: Long,
        createTime: Long,
        remotePasteId: Long,
        pasteFiles: PasteFiles,
        pullChunks: IntArray,
        syncHandler: SyncHandler,
    ): FilePullResult {
        val dateString =
            dateUtils.getYMD(
                dateUtils.epochMillisecondsToLocalDateTime(createTime),
            )
        val isRetry = pullChunks.isNotEmpty()
        val filesIndexBuilder = FilesIndexBuilder(CHUNK_SIZE)
        val renameMap =
            userDataPathProvider.resolve(
                appInstanceId,
                dateString,
                pasteId,
                pasteFiles,
                true,
                filesIndexBuilder,
                resolveConflicts = !isRetry,
            )
        val filesIndex = filesIndexBuilder.build()

        if (filesIndex.getChunkCount() == 0) {
            logger.warn { "No files to pull for pasteId $pasteId, remotePasteId $remotePasteId" }
            return FilePullResult.Empty
        }

        val effectivePullChunks =
            if (pullChunks.isEmpty()) {
                IntArray(filesIndex.getChunkCount())
            } else {
                require(pullChunks.size == filesIndex.getChunkCount()) {
                    "pasteId = $pasteId, Pull chunks count mismatch: " +
                        "pullChunks.size=${pullChunks.size}, chunkCount=${filesIndex.getChunkCount()}"
                }
                pullChunks
            }

        val host =
            syncHandler.getConnectHostAddress()
                ?: return FilePullResult.NoSyncAddress(appInstanceId)

        val port = syncHandler.currentSyncRuntimeInfo.port

        val hostAndPort = HostAndPort(host, port)
        val toUrl: URLBuilder.() -> Unit = {
            buildUrl(hostAndPort)
        }

        val tasks: List<suspend () -> Pair<Int, ClientApiResult>> =
            (0..<filesIndex.getChunkCount())
                .filter { effectivePullChunks[it] == 0 }
                .map { chunkIndex ->
                    {
                        runCatching {
                            filesIndex.getChunk(chunkIndex)?.let { filesChunk ->
                                val pullFileRequest = PullFileRequest(remotePasteId, chunkIndex)
                                val result =
                                    pullClientApi.pullFile(
                                        pullFileRequest,
                                        appInstanceId,
                                        toUrl,
                                    )
                                if (result is SuccessResult) {
                                    val byteReadChannel = result.getResult<ByteReadChannel>()
                                    fileUtils.writeFilesChunk(filesChunk, byteReadChannel)
                                }
                                Pair(chunkIndex, result)
                            } ?: Pair(
                                chunkIndex,
                                createFailureResult(
                                    StandardErrorCode.PULL_FILE_CHUNK_TASK_FAIL,
                                    "chunkIndex $chunkIndex out of range",
                                ),
                            )
                        }.getOrElse {
                            Pair(
                                chunkIndex,
                                createFailureResult(
                                    StandardErrorCode.PULL_FILE_CHUNK_TASK_FAIL,
                                    "pull chunk fail: ${it.message}",
                                ),
                            )
                        }
                    }
                }

        val map = pasteSyncProcessManager.runTask(pasteId, tasks).toMap()

        val successes = map.filter { it.value is SuccessResult }.map { it.key }

        val fails: Map<Int, FailureResult> =
            map
                .filter { it.value is FailureResult }
                .mapValues { it.value as FailureResult }

        successes.forEach {
            effectivePullChunks[it] = 1
        }

        return if (effectivePullChunks.contains(0)) {
            FilePullResult.Failure(fails, effectivePullChunks)
        } else {
            pasteSyncProcessManager.cleanProcess(pasteId)
            FilePullResult.Success(renameMap)
        }
    }
}
