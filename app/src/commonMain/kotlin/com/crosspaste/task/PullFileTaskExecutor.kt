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
import kotlin.coroutines.cancellation.CancellationException

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
                try {
                    filePullService.pullFiles(
                        appInstanceId = pasteData.appInstanceId,
                        pasteId = pasteData.id,
                        createTime = pasteData.createTime,
                        remotePasteId = pullExtraInfo.id,
                        pasteFiles = pasteFiles,
                        pullChunks = pullExtraInfo.pullChunks,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Counted as a failed attempt, so the retry budget still ends in cleanup
                    logger.warn(e) { "Pull files failed for pasteId=${pasteData.id}" }
                    return@let doFailure(
                        pasteData,
                        pullExtraInfo,
                        listOf(createFailureResult(StandardErrorCode.PULL_FILE_TASK_FAIL, "Pull files failed: $e")),
                        pasteTask.modifyTime,
                    )
                }

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
                val pulledPasteData = pasteData.withRenames(result.renameMap)
                if (discardIfDeleted(pulledPasteData)) return SuccessPasteTaskResult()
                if (result.renameMap.isNotEmpty()) {
                    pasteDao.updateFilePath(pulledPasteData)
                }
                pasteboardService
                    .tryWriteRemotePasteboardWithFile(pasteData.id, pullExtraInfo.seenAppInstanceIds)
                    .fold(
                        onSuccess = {
                            soundService.successSound()
                            SuccessPasteTaskResult()
                        },
                        onFailure = { e -> abandonUnfinalized(pulledPasteData, pullExtraInfo, startTime, e) },
                    )
            }

            is FilePullResult.Failure -> {
                val effectivePasteData = pasteData.withRenames(result.renameMap)
                if (discardIfDeleted(effectivePasteData)) return SuccessPasteTaskResult()
                if (result.renameMap.isNotEmpty()) {
                    pasteDao.updateFilePath(effectivePasteData)
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

    private fun PasteData.withRenames(renameMap: Map<String, String>): PasteData =
        if (renameMap.isEmpty()) this else applyRenameMap(renameMap)

    /**
     * The row was deleted (by the user) while its files were being pulled: the delete
     * task ran before the files existed, so reclaim them here, quietly.
     */
    private suspend fun discardIfDeleted(pasteData: PasteData): Boolean {
        if (pasteDao.getNoDeletePasteData(pasteData.id) != null) return false
        pasteSyncProcessManager.cleanProcess(pasteData.id)
        cleanupPullFiles(pasteData)
        return true
    }

    /**
     * Every chunk landed but the row could not be finalized. Retrying would pull the
     * whole paste again, so end it instead of leaving it LOADING forever.
     */
    private suspend fun abandonUnfinalized(
        pasteData: PasteData,
        pullExtraInfo: PullExtraInfo,
        startTime: Long,
        cause: Throwable,
    ): PasteTaskResult {
        if (discardIfDeleted(pasteData)) return SuccessPasteTaskResult()
        logger.error(cause) { "Finalize pulled paste failed, pasteId=${pasteData.id}" }
        pasteSyncProcessManager.cleanProcess(pasteData.id)
        cleanupPullFiles(pasteData)
        pasteDao.markDeletePasteData(pasteData.id)
        soundService.errorSound()
        return TaskUtils.createFailurePasteTaskResult(
            logger = logger,
            retryHandler = { false },
            startTime = startTime,
            fails = listOf(createFailureResult(StandardErrorCode.PULL_FILE_TASK_FAIL, "Finalize failed: $cause")),
            extraInfo = pullExtraInfo,
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
            val managedItems =
                pasteData
                    .getPasteAppearItems()
                    .filterIsInstance<PasteFiles>()
                    .filter { it.basePath == null }
            for (pasteFiles in managedItems) {
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
            }
            userDataPathProvider.deleteReceivedFilesOutsideStorage(pasteData)
        }.onFailure { e ->
            logger.warn(e) { "Failed to clean up pull files" }
        }
    }
}
