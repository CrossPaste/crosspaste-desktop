package com.crosspaste.task

import com.crosspaste.clean.CleanTime
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.PasteType
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.getDateUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CleanPasteTaskExecutor(
    private val pasteDao: PasteDao,
    private val configManager: CommonConfigManager,
) : SingleTypeTaskExecutor {

    private val logger: KLogger = KotlinLogging.logger {}

    private val dateUtils = getDateUtils()

    override val taskType: Int = TaskType.CLEAN_PASTE_TASK

    private val cleanLock = Mutex()

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        val config = configManager.getCurrentConfig()
        if (config.enableExpirationCleanup) {
            runCatching {
                cleanLock.withLock {
                    val imageCleanTimeIndex = config.imageCleanTimeIndex
                    val imageCleanTime = CleanTime.entries[imageCleanTimeIndex]
                    val imageCleanTimeInstant = dateUtils.getOffsetDay(days = -imageCleanTime.days)
                    pasteDao.markDeleteByCleanTime(imageCleanTimeInstant, PasteType.IMAGE_TYPE.type)

                    val fileCleanTimeIndex = config.fileCleanTimeIndex
                    val fileCleanTime = CleanTime.entries[fileCleanTimeIndex]
                    val fileCleanTimeInstant = dateUtils.getOffsetDay(days = -fileCleanTime.days)
                    pasteDao.markDeleteByCleanTime(fileCleanTimeInstant, PasteType.FILE_TYPE.type)
                }
            }.onFailure {
                val baseExtraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class)
                return TaskUtils.createFailurePasteTaskResult(
                    logger = logger,
                    retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                    startTime = pasteTask.modifyTime,
                    fails = listOf(createFailureResult(StandardErrorCode.CLEAN_PASTE_TASK_FAIL, it)),
                    extraInfo = baseExtraInfo,
                )
            }
        }

        if (config.enableThresholdCleanup) {
            runCatching {
                cleanLock.withLock {
                    val allSize = pasteDao.getSize(true)
                    val favoriteSize = pasteDao.getSize(false)
                    val noFavoriteSize = allSize - favoriteSize
                    if (noFavoriteSize > config.maxStorage * 1024 * 1024) {
                        val cleanSize = noFavoriteSize * config.cleanupPercentage / 100
                        deleteStorageOfApproximateSize(cleanSize)
                    }
                }
            }.onFailure {
                val baseExtraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class)
                return TaskUtils.createFailurePasteTaskResult(
                    logger = logger,
                    retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                    startTime = pasteTask.modifyTime,
                    fails = listOf(createFailureResult(StandardErrorCode.CLEAN_PASTE_TASK_FAIL, it)),
                    extraInfo = baseExtraInfo,
                )
            }
        }

        return SuccessPasteTaskResult()
    }

    private suspend fun deleteStorageOfApproximateSize(cleanSize: Long) {
        val cutoffTime = pasteDao.findCleanTimeByCumulativeSize(cleanSize) ?: return
        pasteDao.markDeleteByCleanTime(cutoffTime)
    }
}
