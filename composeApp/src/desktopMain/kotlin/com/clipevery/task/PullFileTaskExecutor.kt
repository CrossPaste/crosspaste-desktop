package com.clipevery.task

import com.clipevery.clip.ClipboardService
import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.TaskType
import com.clipevery.dto.pull.PullFileRequest
import com.clipevery.exception.StandardErrorCode
import com.clipevery.net.clientapi.ClientApiResult
import com.clipevery.net.clientapi.FailureResult
import com.clipevery.net.clientapi.PullClientApi
import com.clipevery.net.clientapi.SuccessResult
import com.clipevery.net.clientapi.createFailureResult
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.FilesIndex
import com.clipevery.presist.FilesIndexBuilder
import com.clipevery.sync.SyncManager
import com.clipevery.task.extra.PullExtraInfo
import com.clipevery.utils.DateUtils
import com.clipevery.utils.FileUtils
import com.clipevery.utils.TaskUtils
import com.clipevery.utils.TaskUtils.createFailureClipTaskResult
import com.clipevery.utils.buildUrl
import com.clipevery.utils.getDateUtils
import com.clipevery.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.utils.io.*

class PullFileTaskExecutor(
    private val clipDao: ClipDao,
    private val pullClientApi: PullClientApi,
    private val syncManager: SyncManager,
    private val clipboardService: ClipboardService,
) : SingleTypeTaskExecutor {

    companion object PullFileTaskExecutor {

        private val logger = KotlinLogging.logger {}

        const val CHUNK_SIZE: Long = 1024 * 1024 // 1MB

        private val dateUtils: DateUtils = getDateUtils()

        private val fileUtils: FileUtils = getFileUtils()
    }

    override val taskType: Int = TaskType.PULL_FILE_TASK

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        val pullExtraInfo: PullExtraInfo = TaskUtils.getExtraInfo(clipTask, PullExtraInfo::class)

        clipDao.getClipData(clipTask.clipDataId!!)?.let { clipData ->
            val fileItems = clipData.getClipAppearItems().filter { it is ClipFiles }
            val appInstanceId = clipData.appInstanceId
            val dateString =
                dateUtils.getYYYYMMDD(
                    dateUtils.convertRealmInstantToLocalDateTime(clipData.createTime),
                )
            val clipId = clipData.clipId
            val filesIndexBuilder = FilesIndexBuilder(CHUNK_SIZE)
            for (clipAppearItem in fileItems) {
                val clipFiles = clipAppearItem as ClipFiles
                DesktopPathProvider.resolve(appInstanceId, dateString, clipId, clipFiles, true, filesIndexBuilder)
            }
            val filesIndex = filesIndexBuilder.build()

            if (filesIndex.getChunkCount() == 0) {
                return SuccessClipTaskResult()
            }

            if (pullExtraInfo.pullChunks.isEmpty()) {
                pullExtraInfo.pullChunks.addAll(List(filesIndex.getChunkCount()) { false })
            } else if (pullExtraInfo.pullChunks.size != filesIndex.getChunkCount()) {
                throw IllegalArgumentException("pullChunks size is not equal to chunkNum")
            }

            syncManager.getSyncHandlers()[appInstanceId]?.let {
                val port = it.syncRuntimeInfo.port

                it.getConnectHostAddress()?.let { host ->
                    return pullFiles(clipData, host, port, filesIndex, pullExtraInfo, 8)
                } ?: run {
                    return createFailureClipTaskResult(
                        logger = logger,
                        retryHandler = { pullExtraInfo.executionHistories.size < 3 },
                        startTime = clipTask.modifyTime,
                        fails =
                            listOf(
                                createFailureResult(
                                    StandardErrorCode.CANT_GET_SYNC_ADDRESS,
                                    "Failed to get connect host address by $appInstanceId",
                                ),
                            ),
                        extraInfo = pullExtraInfo,
                    )
                }
            } ?: run {
                return createFailureClipTaskResult(
                    logger = logger,
                    retryHandler = { pullExtraInfo.executionHistories.size < 3 },
                    startTime = clipTask.modifyTime,
                    fails =
                        listOf(
                            createFailureResult(
                                StandardErrorCode.PULL_FILE_TASK_FAIL,
                                "Failed to get sync handler by $appInstanceId",
                            ),
                        ),
                    extraInfo = pullExtraInfo,
                )
            }
        } ?: run {
            return SuccessClipTaskResult()
        }
    }

    private suspend fun pullFiles(
        clipData: ClipData,
        host: String,
        port: Int,
        filesIndex: FilesIndex,
        pullExtraInfo: PullExtraInfo,
        concurrencyLevel: Int,
    ): ClipTaskResult {
        val toUrl: URLBuilder.(URLBuilder) -> Unit = { urlBuilder: URLBuilder ->
            buildUrl(urlBuilder, host, port, "pull", "file")
        }

        val tasks: List<suspend () -> Pair<Int, ClientApiResult>> =
            (0..<filesIndex.getChunkCount())
                .filter { !pullExtraInfo.pullChunks[it] }
                .map { chunkIndex ->
                    {
                        try {
                            filesIndex.getChunk(chunkIndex)?.let { filesChunk ->
                                val pullFileRequest = PullFileRequest(clipData.appInstanceId, clipData.clipId, chunkIndex)
                                val result = pullClientApi.pullFile(pullFileRequest, toUrl)
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
                        } catch (e: Exception) {
                            Pair(
                                chunkIndex,
                                createFailureResult(
                                    StandardErrorCode.PULL_FILE_CHUNK_TASK_FAIL,
                                    "pull chunk fail: ${e.message}",
                                ),
                            )
                        }
                    }
                }

        val map = TaskSemaphore.withIoTaskLimit(concurrencyLevel, tasks).toMap()

        val successes = map.filter { it.value is SuccessResult }.map { it.key }

        val fails: Map<Int, FailureResult> =
            map
                .filter { it.value is FailureResult }
                .mapValues { it.value as FailureResult }

        successes.forEach {
            pullExtraInfo.pullChunks[it] = true
        }

        return if (pullExtraInfo.pullChunks.contains(false)) {
            val needRetry = pullExtraInfo.executionHistories.size < 3

            if (!needRetry) {
                logger.error { "exist pull chunk fail" }
                clipboardService.clearRemoteClipboard(clipData)
            }

            createFailureClipTaskResult(
                logger = logger,
                retryHandler = { needRetry },
                startTime = System.currentTimeMillis(),
                fails = fails.values,
                extraInfo = pullExtraInfo,
            )
        } else {
            clipboardService.tryWriteRemoteClipboardWithFile(clipData)
            SuccessClipTaskResult()
        }
    }
}
