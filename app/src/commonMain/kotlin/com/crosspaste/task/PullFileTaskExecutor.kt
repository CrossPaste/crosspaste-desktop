package com.crosspaste.task

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.PullExtraInfo
import com.crosspaste.db.task.TaskType
import com.crosspaste.dto.pull.PullFileRequest
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesIndex
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.sound.SoundService
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.utils.io.*

class PullFileTaskExecutor(
    private val pasteDao: PasteDao,
    private val pullClientApi: PullClientApi,
    private val userDataPathProvider: UserDataPathProvider,
    private val pasteSyncProcessManager: PasteSyncProcessManager<Long>,
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

        if (pasteTask.pasteDataId == null) {
            return TaskUtils.createFailurePasteTaskResult(
                logger = logger,
                retryHandler = { false },
                startTime = pasteTask.modifyTime,
                fails =
                    listOf(
                        createFailureResult(
                            StandardErrorCode.PULL_FILE_TASK_FAIL,
                            "pasteDataId is null",
                        ),
                    ),
                extraInfo = pullExtraInfo,
            )
        }

        return pasteDao.getNoDeletePasteData(pasteTask.pasteDataId)?.let { pasteData ->
            val fileItems = pasteData.getPasteAppearItems().filter { it is PasteFiles }
            if (fileItems.size != 1) {
                return@let doFailure(
                    pasteData,
                    pullExtraInfo,
                    listOf(
                        createFailureResult(
                            StandardErrorCode.PULL_FILE_TASK_FAIL,
                            "Expected exactly 1 PasteFiles item, got ${fileItems.size}",
                        ),
                    ),
                    pasteTask.modifyTime,
                )
            }
            val fileItem = fileItems.first()
            val appInstanceId = pasteData.appInstanceId
            val dateString =
                dateUtils.getYMD(
                    dateUtils.epochMillisecondsToLocalDateTime(pasteData.createTime),
                )
            val id = pasteData.id
            val filesIndexBuilder = FilesIndexBuilder(CHUNK_SIZE)
            val pasteFiles = fileItem as PasteFiles
            val isRetry = pullExtraInfo.pullChunks.isNotEmpty()
            val renameMap =
                userDataPathProvider.resolve(
                    appInstanceId,
                    dateString,
                    id,
                    pasteFiles,
                    true,
                    filesIndexBuilder,
                    resolveConflicts = !isRetry,
                )
            val filesIndex = filesIndexBuilder.build()

            if (filesIndex.getChunkCount() == 0) {
                return@doExecuteTask SuccessPasteTaskResult()
            }

            if (pullExtraInfo.pullChunks.isEmpty()) {
                pullExtraInfo.pullChunks = IntArray(filesIndex.getChunkCount())
            } else if (pullExtraInfo.pullChunks.size != filesIndex.getChunkCount()) {
                throw IllegalArgumentException("pullChunks size is not equal to chunkNum")
            }

            syncManager.getSyncHandlers()[appInstanceId]?.let {
                val port = it.currentSyncRuntimeInfo.port

                it.getConnectHostAddress()?.let { host ->
                    pullFiles(pasteData, host, port, filesIndex, pullExtraInfo, renameMap)
                } ?: run {
                    doFailure(
                        pasteData,
                        pullExtraInfo,
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
                doFailure(
                    pasteData,
                    pullExtraInfo,
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
            SuccessPasteTaskResult()
        }
    }

    private suspend fun pullFiles(
        pasteData: PasteData,
        host: String,
        port: Int,
        filesIndex: FilesIndex,
        pullExtraInfo: PullExtraInfo,
        renameMap: Map<String, String>,
    ): PasteTaskResult {
        val hostAndPort = HostAndPort(host, port)
        val toUrl: URLBuilder.() -> Unit = {
            buildUrl(hostAndPort)
        }

        val tasks: List<suspend () -> Pair<Int, ClientApiResult>> =
            (0..<filesIndex.getChunkCount())
                .filter { pullExtraInfo.pullChunks[it] == 0 }
                .map { chunkIndex ->
                    {
                        runCatching {
                            filesIndex.getChunk(chunkIndex)?.let { filesChunk ->
                                val pullFileRequest = PullFileRequest(pullExtraInfo.id, chunkIndex)
                                val result =
                                    pullClientApi.pullFile(
                                        pullFileRequest,
                                        pasteData.appInstanceId,
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
            if (renameMap.isNotEmpty()) {
                val updatedPasteData = applyRenameMapToPasteData(pasteData, renameMap)
                pasteDao.updateFilePath(updatedPasteData)
            }
            pasteboardService.tryWriteRemotePasteboardWithFile(pasteData)
            soundService.successSound()
            SuccessPasteTaskResult()
        }
    }

    private fun applyRenameMapToPasteData(
        pasteData: PasteData,
        renameMap: Map<String, String>,
    ): PasteData {
        val updatedAppearItem =
            (pasteData.pasteAppearItem as? PasteFiles)?.applyRenameMap(renameMap) as? PasteItem
                ?: pasteData.pasteAppearItem
        val updatedCollectionItems =
            pasteData.pasteCollection.pasteItems.map { item ->
                (item as? PasteFiles)?.applyRenameMap(renameMap) as? PasteItem ?: item
            }
        return pasteData.copy(
            pasteAppearItem = updatedAppearItem,
            pasteCollection = PasteCollection(updatedCollectionItems),
        )
    }

    private suspend fun doFailure(
        pasteData: PasteData,
        pullExtraInfo: PullExtraInfo,
        fails: Collection<FailureResult>,
        startTime: Long = nowEpochMilliseconds(),
    ): PasteTaskResult {
        val needRetry = pullExtraInfo.executionHistories.size < 3

        if (!needRetry) {
            logger.error { "exist pull chunk fail" }
            cleanupPullFiles(pasteData)
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

    private fun cleanupPullFiles(pasteData: PasteData) {
        runCatching {
            val fileItems = pasteData.getPasteAppearItems().filterIsInstance<PasteFiles>()
            for (pasteFiles in fileItems) {
                if (pasteFiles.basePath == null) {
                    // Managed storage: delete the paste directory
                    val dateString =
                        dateUtils.getYMD(
                            dateUtils.epochMillisecondsToLocalDateTime(pasteData.createTime),
                        )
                    val basePath =
                        userDataPathProvider
                            .resolve(appFileType = pasteFiles.getAppFileType())
                            .resolve(pasteData.appInstanceId)
                            .resolve(dateString)
                            .resolve(pasteData.id.toString())
                    fileUtils.fileSystem.deleteRecursively(basePath)
                } else {
                    // Download directory: delete individual pre-allocated files
                    for (filePath in pasteFiles.getFilePaths(userDataPathProvider)) {
                        fileUtils.deleteFile(filePath)
                    }
                }
            }
        }.onFailure { e ->
            logger.warn(e) { "Failed to clean up pull files" }
        }
    }
}
