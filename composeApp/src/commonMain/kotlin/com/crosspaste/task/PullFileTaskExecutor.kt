package com.crosspaste.task

import com.crosspaste.dto.pull.PullFileRequest
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesIndex
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.realm.task.PasteTask
import com.crosspaste.realm.task.PullExtraInfo
import com.crosspaste.realm.task.TaskType
import com.crosspaste.sound.SoundService
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.DateUtils.toLocalDateTime
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.datetime.Clock
import org.mongodb.kbson.ObjectId

class PullFileTaskExecutor(
    private val pasteRealm: PasteRealm,
    private val pullClientApi: PullClientApi,
    private val userDataPathProvider: UserDataPathProvider,
    private val pasteSyncProcessManager: PasteSyncProcessManager<ObjectId>,
    private val pasteboardService: PasteboardService,
    private val soundService: SoundService,
    private val syncManager: SyncManager,
) : SingleTypeTaskExecutor {

    companion object PullFileTaskExecutor {

        private val logger = KotlinLogging.logger {}

        const val CHUNK_SIZE: Long = 1024 * 1024 // 1MB

        private val dateUtils: DateUtils = getDateUtils()

        private val fileUtils: FileUtils = getFileUtils()
    }

    override val taskType: Int = TaskType.PULL_FILE_TASK

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        val pullExtraInfo: PullExtraInfo = TaskUtils.getExtraInfo(pasteTask, PullExtraInfo::class)

        pasteRealm.getPasteData(pasteTask.pasteDataId!!)?.let { pasteData ->
            val fileItems = pasteData.getPasteAppearItems().filter { it is PasteFiles }
            val appInstanceId = pasteData.appInstanceId
            val dateString = dateUtils.getYMD(pasteData.createTime.toLocalDateTime())
            val pasteId = pasteData.pasteId
            val filesIndexBuilder = FilesIndexBuilder(CHUNK_SIZE)
            for (pasteAppearItem in fileItems) {
                val pasteFiles = pasteAppearItem as PasteFiles
                userDataPathProvider.resolve(appInstanceId, dateString, pasteId, pasteFiles, true, filesIndexBuilder)
            }
            val filesIndex = filesIndexBuilder.build()

            if (filesIndex.getChunkCount() == 0) {
                return SuccessPasteTaskResult()
            }

            if (pullExtraInfo.pullChunks.isEmpty()) {
                pullExtraInfo.pullChunks = IntArray(filesIndex.getChunkCount())
            } else if (pullExtraInfo.pullChunks.size != filesIndex.getChunkCount()) {
                throw IllegalArgumentException("pullChunks size is not equal to chunkNum")
            }

            syncManager.getSyncHandlers()[appInstanceId]?.let {
                val port = it.syncRuntimeInfo.port

                it.getConnectHostAddress()?.let { host ->
                    return pullFiles(pasteData, host, port, filesIndex, pullExtraInfo)
                } ?: run {
                    return doFailure(
                        pasteData, pullExtraInfo,
                        listOf(
                            createFailureResult(
                                StandardErrorCode.CANT_GET_SYNC_ADDRESS,
                                "Failed to get connect host address by $appInstanceId",
                            ),
                        ),
                        pasteTask.modifyTime,
                    )
                }
            } ?: run {
                return doFailure(
                    pasteData, pullExtraInfo,
                    listOf(
                        createFailureResult(
                            StandardErrorCode.PULL_FILE_TASK_FAIL,
                            "Failed to get sync handler by $appInstanceId",
                        ),
                    ),
                    pasteTask.modifyTime,
                )
            }
        } ?: run {
            return SuccessPasteTaskResult()
        }
    }

    private suspend fun pullFiles(
        pasteData: PasteData,
        host: String,
        port: Int,
        filesIndex: FilesIndex,
        pullExtraInfo: PullExtraInfo,
    ): PasteTaskResult {
        val toUrl: URLBuilder.() -> Unit = {
            buildUrl(host, port)
        }

        val tasks: List<suspend () -> Pair<Int, ClientApiResult>> =
            (0..<filesIndex.getChunkCount())
                .filter { pullExtraInfo.pullChunks[it] == 0 }
                .map { chunkIndex ->
                    {
                        try {
                            filesIndex.getChunk(chunkIndex)?.let { filesChunk ->
                                val pullFileRequest = PullFileRequest(pasteData.appInstanceId, pasteData.pasteId, chunkIndex)
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

        val map = pasteSyncProcessManager.runTask(pasteData.id, tasks).toMap()

        val successes = map.filter { it.value is SuccessResult }.map { it.key }

        val fails: Map<Int, FailureResult> =
            map
                .filter { it.value is FailureResult }
                .mapValues { it.value as FailureResult }

        successes.forEach {
            pullExtraInfo.pullChunks[it] = 1
        }

        return if (pullExtraInfo.pullChunks.contains(0)) {
            doFailure(pasteData, pullExtraInfo, fails.values)
        } else {
            pasteSyncProcessManager.cleanProcess(pasteData.id)
            pasteboardService.tryWriteRemotePasteboardWithFile(pasteData)
            soundService.successSound()
            SuccessPasteTaskResult()
        }
    }

    private suspend fun doFailure(
        pasteData: PasteData,
        pullExtraInfo: PullExtraInfo,
        fails: Collection<FailureResult>,
        startTime: Long = Clock.System.now().toEpochMilliseconds(),
    ): PasteTaskResult {
        val needRetry = pullExtraInfo.executionHistories.size < 3

        if (!needRetry) {
            logger.error { "exist pull chunk fail" }
            pasteboardService.clearRemotePasteboard(pasteData)
            soundService.errorSound()
        }

        return TaskUtils.createFailurePasteTaskResult(
            logger = logger,
            retryHandler = { needRetry },
            startTime = startTime,
            fails = fails,
            extraInfo = pullExtraInfo,
        )
    }
}
