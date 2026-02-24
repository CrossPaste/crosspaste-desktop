package com.crosspaste.sync

import com.crosspaste.dto.pull.PullFileRequest
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.URLBuilder
import io.ktor.utils.io.ByteReadChannel

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
) {

    companion object {

        private val logger = KotlinLogging.logger {}

        const val CHUNK_SIZE: Long = 1024 * 1024 // 1MB

        private val dateUtils: DateUtils = getDateUtils()

        private val fileUtils: FileUtils = getFileUtils()
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

        val syncHandler =
            syncManager.getSyncHandlers()[appInstanceId]
                ?: return FilePullResult.NoSyncHandler(appInstanceId)

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
