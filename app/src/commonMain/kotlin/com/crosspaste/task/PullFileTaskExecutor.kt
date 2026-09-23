package com.crosspaste.task

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.PullExtraInfo
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.applyRenameMap
import com.crosspaste.paste.item.getAppFileType
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.sound.SoundService
import com.crosspaste.sync.FilePullResult
import com.crosspaste.sync.FilePullService
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging

class PullFileTaskExecutor(
    private val filePullService: FilePullService,
    private val pasteDao: PasteDao,
    private val pasteSyncProcessManager: PasteSyncProcessManager<Long>,
    private val pasteboardService: PasteboardService,
    private val soundService: SoundService,
    private val userDataPathProvider: UserDataPathProvider,
) : SingleTypeTaskExecutor {

    companion object PullFileTaskExecutor {

        private val logger = KotlinLogging.logger {}

        private val dateUtils: DateUtils = getDateUtils()

        private val fileUtils: FileUtils = getFileUtils()
    }

    override val taskType: Int = TaskType.PULL_FILE_TASK

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        val pullExtraInfo: PullExtraInfo = TaskUtils.getExtraInfo(pasteTask, PullExtraInfo::class)

        val pasteDataId =
            pasteTask.pasteDataId
                ?: return TaskUtils.createFailurePasteTaskResult(
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

        return pasteDao.getNoDeletePasteData(pasteDataId)?.let { pasteData ->
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

            val pasteFiles = fileItems.first() as PasteFiles

            val result =
                filePullService.pullFiles(
                    appInstanceId = pasteData.appInstanceId,
                    pasteId = pasteData.id,
                    createTime = pasteData.createTime,
                    remotePasteId = pullExtraInfo.id,
                    pasteFiles = pasteFiles,
                    pullChunks = pullExtraInfo.pullChunks,
                )

            handleResult(result, pasteData, pullExtraInfo, pasteTask.modifyTime)
        } ?: SuccessPasteTaskResult()
    }

    private suspend fun handleResult(
        result: FilePullResult,
        pasteData: PasteData,
        pullExtraInfo: PullExtraInfo,
        startTime: Long,
    ): PasteTaskResult =
        when (result) {
            is FilePullResult.Empty -> SuccessPasteTaskResult()

            is FilePullResult.Success -> {
                if (result.renameMap.isNotEmpty()) {
                    val updatedPasteData = pasteData.applyRenameMap(result.renameMap)
                    pasteDao.updateFilePath(updatedPasteData)
                }
                pasteboardService.tryWriteRemotePasteboardWithFile(pasteData.id)
                soundService.successSound()
                SuccessPasteTaskResult()
            }

            is FilePullResult.Failure -> {
                val effectivePasteData =
                    if (result.renameMap.isNotEmpty()) {
                        pasteData.applyRenameMap(result.renameMap).also {
                            pasteDao.updateFilePath(it)
                        }
                    } else {
                        pasteData
                    }
                pullExtraInfo.pullChunks = result.pullChunks
                doFailure(effectivePasteData, pullExtraInfo, result.failedChunks.values, startTime)
            }

            is FilePullResult.NoSyncHandler -> {
                doFailure(
                    pasteData,
                    pullExtraInfo,
                    listOf(
                        createFailureResult(
                            StandardErrorCode.PULL_FILE_TASK_FAIL,
                            "Failed to get sync handler by ${result.appInstanceId}",
                        ),
                    ),
                    startTime,
                )
            }

            is FilePullResult.NoSyncAddress -> {
                doFailure(
                    pasteData,
                    pullExtraInfo,
                    listOf(
                        createFailureResult(
                            StandardErrorCode.CANT_GET_SYNC_ADDRESS,
                            "Failed to get connect host address by ${result.appInstanceId}",
                        ),
                    ),
                    startTime,
                )
            }
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
            pasteSyncProcessManager.cleanProcess(pasteData.id)
            cleanupPullFiles(pasteData)
            pasteDao.markDeletePasteData(pasteData.id)
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
