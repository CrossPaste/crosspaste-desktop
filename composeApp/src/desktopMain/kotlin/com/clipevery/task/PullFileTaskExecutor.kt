package com.clipevery.task

import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.TaskType
import com.clipevery.dto.pull.PullFileRequest
import com.clipevery.net.clientapi.ClientApiResult
import com.clipevery.net.clientapi.FailureResult
import com.clipevery.net.clientapi.PullFileClientApi
import com.clipevery.net.clientapi.SuccessResult
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.FilesIndex
import com.clipevery.presist.FilesIndexBuilder
import com.clipevery.sync.SyncManager
import com.clipevery.task.extra.PullExtraInfo
import com.clipevery.utils.FileUtils
import com.clipevery.utils.TaskUtils
import com.clipevery.utils.TaskUtils.createFailureClipTaskResult
import com.clipevery.utils.buildUrl
import com.clipevery.utils.cpuDispatcher
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class PullFileTaskExecutor(private val lazyClipDao: Lazy<ClipDao>,
                           private val pullFileClientApi: PullFileClientApi,
                           private val fileUtils: FileUtils,
                           private val syncManager: SyncManager): SingleTypeTaskExecutor {

    companion object PullFileTaskExecutor {

        private val logger = KotlinLogging.logger {}

        const val CHUNK_SIZE: Long = 4096 * 1024 // 4MB
    }

    private val clipDao: ClipDao by lazy { lazyClipDao.value }

    override val taskType: Int = TaskType.PULL_FILE_TASK

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        val pullExtraInfo: PullExtraInfo = TaskUtils.getExtraInfo(clipTask, PullExtraInfo::class)

        clipDao.getClipData(clipTask.clipDataId)?.let { clipData ->
            val fileItems = clipData.getClipAppearItems().filter { it is ClipFiles }
            val appInstanceId = clipData.appInstanceId
            val clipId = clipData.clipId
            val filesIndexBuilder = FilesIndexBuilder(CHUNK_SIZE)
            for (clipAppearItem in fileItems) {
                val clipFiles = clipAppearItem as ClipFiles
                DesktopPathProvider.resolve(appInstanceId, clipId, clipFiles, true, filesIndexBuilder)
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
                    return pullFiles(appInstanceId, clipId, host, port, filesIndex, pullExtraInfo, 4)
                } ?: run {
                    return createFailureClipTaskResult(
                        retryHandler = { pullExtraInfo.executionHistories.size < 3 },
                        startTime = clipTask.modifyTime,
                        failMessage = "Failed to get connect host address by $appInstanceId",
                        extraInfo = pullExtraInfo
                    )
                }
            } ?: run {
                return createFailureClipTaskResult(
                    retryHandler = { pullExtraInfo.executionHistories.size < 3 },
                    startTime = clipTask.modifyTime,
                    failMessage = "Failed to get sync handler by $appInstanceId",
                    extraInfo = pullExtraInfo
                )
            }
        } ?: run {
            return SuccessClipTaskResult()
        }
    }

    private suspend fun pullFiles(appInstanceId: String,
                                  clipId: Int,
                                  host: String,
                                  port: Int,
                                  filesIndex: FilesIndex,
                                  pullExtraInfo: PullExtraInfo,
                                  concurrencyLevel: Int): ClipTaskResult {
        val supervisorJob = SupervisorJob()
        val scope = CoroutineScope(cpuDispatcher + supervisorJob)
        val channel = Channel<Int>(Channel.UNLIMITED)
        val results = mutableListOf<Deferred<Pair<Int, ClientApiResult>>>()
        val toUrl: URLBuilder.(URLBuilder) -> Unit = { urlBuilder: URLBuilder ->
            buildUrl(urlBuilder, host, port, "pull", "file")
        }
        repeat(concurrencyLevel) {
            scope.launch {
                for (chunkIndex in channel) {
                    val result = async(ioDispatcher + SupervisorJob()) {
                        try {
                            filesIndex.getChunk(chunkIndex)?.let { filesChunk ->
                                val pullFileRequest = PullFileRequest(appInstanceId, clipId, chunkIndex)
                                val result = pullFileClientApi.pullFile(pullFileRequest, toUrl)
                                if (result is SuccessResult) {
                                    val byteReadChannel = result.getResult<ByteReadChannel>()
                                    fileUtils.writeFilesChunk(filesChunk, byteReadChannel)
                                }
                                return@async Pair(chunkIndex, result)
                            }
                            return@async Pair(chunkIndex, FailureResult("chunkIndex $chunkIndex out of range: " +
                                    "$chunkIndex count: ${filesIndex.getChunkCount()}"))
                        } catch (e: Exception) {
                            logger.error(e) { "pull chunk fail" }
                            return@async Pair(chunkIndex, FailureResult("pull chunk fail: ${e.message}"))
                        }
                    }
                    results.add(result)
                }
            }
        }

        for (i in 0 until filesIndex.getChunkCount()) {
            channel.send(i)
        }

        channel.close()

        val map = results.associate { it.await() }

        val successes = map.filter { it.value is SuccessResult }.map { it.key }

        successes.forEach {
            pullExtraInfo.pullChunks[it] = true
        }

        return if (pullExtraInfo.pullChunks.contains(false)) {
            createFailureClipTaskResult(
                retryHandler = { true },
                startTime = System.currentTimeMillis(),
                failMessage = "exist pull chunk fail",
                extraInfo = pullExtraInfo
            )
        } else {
            SuccessClipTaskResult()
        }
    }
}