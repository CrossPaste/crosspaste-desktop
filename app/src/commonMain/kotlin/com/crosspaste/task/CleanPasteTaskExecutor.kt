package com.crosspaste.task

import com.crosspaste.clean.CleanTime
import com.crosspaste.config.ConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteType
import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.getDateUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CleanPasteTaskExecutor(
    private val pasteDao: PasteDao,
    private val configManager: ConfigManager,
) : SingleTypeTaskExecutor {

    private val logger: KLogger = KotlinLogging.logger {}

    private val dateUtils = getDateUtils()

    override val taskType: Int = TaskType.CLEAN_PASTE_TASK

    private val cleanLock = Mutex()

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        if (configManager.config.enableThresholdCleanup) {
            runCatching {
                cleanLock.withLock {
                    val imageCleanTime = CleanTime.entries[configManager.config.imageCleanTimeIndex]
                    val imageCleanTimeInstant = dateUtils.getOffsetDay(days = -imageCleanTime.days)
                    pasteDao.markDeleteByCleanTime(imageCleanTimeInstant, PasteType.IMAGE_TYPE.type)

                    val fileCleanTime = CleanTime.entries[configManager.config.fileCleanTimeIndex]
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

        if (configManager.config.enableThresholdCleanup) {
            runCatching {
                cleanLock.withLock {
                    val allSize = pasteDao.getSize(true)
                    val favoriteSize = pasteDao.getSize(false)
                    val noFavoriteSize = allSize - favoriteSize
                    if (noFavoriteSize > configManager.config.maxStorage * 1024 * 1024) {
                        val cleanSize = noFavoriteSize * configManager.config.cleanupPercentage / 100

                        deleteStorageOfApproximateSize(cleanSize, noFavoriteSize)
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

    private suspend fun deleteStorageOfApproximateSize(
        minSize: Long,
        totalSize: Long,
    ) {
        // If there's no data, return immediately
        val minTime = pasteDao.getMinPasteDataCreateTime() ?: return
        val currentTime = DateUtils.nowEpochMilliseconds()

        val proportion = minSize.toDouble() / totalSize
        val estimatedDuration = currentTime - minTime
        val targetDuration = (estimatedDuration * proportion).toLong()
        val estimatedTargetTime = minTime + targetDuration

        var left = minTime
        var right = currentTime
        var targetTime = estimatedTargetTime

        var size = pasteDao.getSizeByTimeLessThan(targetTime)
        if (size > minSize) {
            right = targetTime
        } else if (size < minSize) {
            left = targetTime
        }

        while (left <= right) {
            val mid = (left + right) / 2
            size = pasteDao.getSizeByTimeLessThan(mid)

            if (size >= minSize) {
                targetTime = mid
                right = mid - 1
            } else {
                left = mid + 1
            }
        }

        pasteDao.markDeleteByCleanTime(targetTime)
    }
}
