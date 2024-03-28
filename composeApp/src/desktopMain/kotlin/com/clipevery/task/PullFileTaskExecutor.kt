package com.clipevery.task

import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.TaskType
import com.clipevery.dto.pull.PullFileRequest
import com.clipevery.net.clientapi.PullFileClientApi
import com.clipevery.net.clientapi.SuccessResult
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.FilesIndex
import com.clipevery.presist.FilesIndexBuilder
import com.clipevery.sync.SyncManager
import com.clipevery.task.extra.PullExtraInfo
import com.clipevery.utils.TaskUtils
import com.clipevery.utils.TaskUtils.createFailureClipTaskResult
import com.clipevery.utils.buildUrl
import com.clipevery.utils.cpuDispatcher
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class PullFileTaskExecutor(private val lazyClipDao: Lazy<ClipDao>,
                           private val pullFileClientApi: PullFileClientApi,
                           private val syncManager: SyncManager): SingleTypeTaskExecutor {

    companion object PullFileTaskExecutor {

        private val logger = KotlinLogging.logger {}

        private val CHUNK_SIZE: Int = 4096 * 1024 // 4MB
    }

    private val clipDao: ClipDao by lazy { lazyClipDao.value }

    override val taskType: Int = TaskType.PULL_FILE_TASK

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        val pullExtraInfo: PullExtraInfo = TaskUtils.getExtraInfo(clipTask, PullExtraInfo::class)

        clipDao.getClipData(clipTask.clipId)?.let { clipData ->
            val fileItems = clipData.getClipAppearItems().filter { it is ClipFiles }
            val appInstanceId = clipData.appInstanceId
            val clipId = clipData.clipId
            val filesIndexBuilder = FilesIndexBuilder()
            for (clipAppearItem in fileItems) {
                val clipFiles = clipAppearItem as ClipFiles
                DesktopPathProvider.resolve(appInstanceId, clipId, clipFiles, filesIndexBuilder)
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
                    pullFiles(appInstanceId, clipId, host, port, filesIndex, 4)
                    return SuccessClipTaskResult()
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

    private fun pullFiles(appInstanceId: String,
                          clipId: Int,
                          host: String,
                          port: Int,
                          filesIndex: FilesIndex,
                          concurrencyLevel: Int) {
        val supervisorJob = SupervisorJob()
        val scope = CoroutineScope(cpuDispatcher + supervisorJob)
        val channel = Channel<Int>(Channel.UNLIMITED)
        val results = mutableListOf<Deferred<Pair<Int, Boolean>>>() // 使用Nullable Boolean来处理可能的失败情况
        val toUrl: URLBuilder.(URLBuilder) -> Unit = { urlBuilder: URLBuilder ->
            buildUrl(urlBuilder, host, port, "pull", "file")
        }
        repeat(concurrencyLevel) {
            scope.launch {
                for (chunkIndex in channel) {
                    val result = async(ioDispatcher + SupervisorJob()) {
                        try {
                            filesIndex.getChunk(chunkIndex)?.let {
                                val pullFileRequest = PullFileRequest(appInstanceId, clipId, chunkIndex)
                                val result = pullFileClientApi.pullFile(pullFileRequest, toUrl)
                                if (result is SuccessResult) {

                                } else {
                                    return@async Pair(chunkIndex, false)
                                }
                            }
                        } catch (e: Exception) {
                            logger.error(e) { "pull chunk fail" }
                            return@async Pair(chunkIndex, false)
                        }
                    }
                    results.add(result)
                }
            }
        }

        filesIndex.forEach {
            channel.send(it)
        }
        channel.close()

        // 等待所有结果完成并收集，过滤掉因异常而为null的结果
        results.awaitAll().filterNotNull()
    }
}