package com.crosspaste.task

import com.crosspaste.clean.CleanTime
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.dao.task.PasteTask
import com.crosspaste.dao.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.task.extra.BaseExtraInfo
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.DesktopTaskUtils
import com.crosspaste.utils.DesktopTaskUtils.createFailurePasteTaskResult
import com.crosspaste.utils.getDateUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CleanPasteTaskExecutor(
    private val pasteDao: PasteDao,
    private val configManager: ConfigManager,
) : SingleTypeTaskExecutor {

    private val logger: KLogger = KotlinLogging.logger {}

    private val dateUtils: DateUtils = getDateUtils()

    override val taskType: Int = TaskType.CLEAN_PASTE_TASK

    private val cleanLock = Mutex()

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        if (configManager.config.isThresholdCleanup) {
            try {
                cleanLock.withLock {
                    val imageCleanTime = CleanTime.entries[configManager.config.imageCleanTimeIndex]
                    val imageCleanTimeInstant = dateUtils.getRealmInstant(-imageCleanTime.days)
                    pasteDao.markDeleteByCleanTime(imageCleanTimeInstant, PasteType.IMAGE)

                    val fileCleanTime = CleanTime.entries[configManager.config.fileCleanTimeIndex]
                    val fileCleanTimeInstant = dateUtils.getRealmInstant(-fileCleanTime.days)
                    pasteDao.markDeleteByCleanTime(fileCleanTimeInstant, PasteType.FILE)
                }
            } catch (e: Throwable) {
                val baseExtraInfo = DesktopTaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class)
                return createFailurePasteTaskResult(
                    logger = logger,
                    retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                    startTime = pasteTask.modifyTime,
                    fails = listOf(createFailureResult(StandardErrorCode.CLEAN_TASK_FAIL, e)),
                    extraInfo = baseExtraInfo,
                )
            }
        }

        if (configManager.config.isThresholdCleanup) {
            try {
                cleanLock.withLock {
                    val allSize = pasteDao.getSize(true)
                    val favoriteSize = pasteDao.getSize(false)
                    val noFavoriteSize = allSize - favoriteSize
                    if (noFavoriteSize > configManager.config.maxStorage * 1024 * 1024) {
                        val cleanSize = noFavoriteSize * configManager.config.cleanupPercentage / 100

                        deleteStorageOfApproximateSize(cleanSize, noFavoriteSize)
                    }
                }
            } catch (e: Throwable) {
                val baseExtraInfo = DesktopTaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class)
                return createFailurePasteTaskResult(
                    logger = logger,
                    retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                    startTime = pasteTask.modifyTime,
                    fails = listOf(createFailureResult(StandardErrorCode.CLEAN_TASK_FAIL, e)),
                    extraInfo = baseExtraInfo,
                )
            }
        }

        return SuccessPasteTaskResult()
    }

    suspend fun deleteStorageOfApproximateSize(
        minSize: Long,
        totalSize: Long,
    ) {
        val minTime = pasteDao.getMinPasteDataCreateTime() ?: return // 如果没有数据，则直接返回
        val currentTime = RealmInstant.now()

        val proportion = minSize.toDouble() / totalSize
        val estimatedDuration = currentTime.epochSeconds - minTime.epochSeconds
        val targetDuration = (estimatedDuration * proportion).toLong()
        val estimatedTargetTime = RealmInstant.from(minTime.epochSeconds + targetDuration, minTime.nanosecondsOfSecond)

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
            val mid =
                RealmInstant.from(
                    (left.epochSeconds + right.epochSeconds) / 2,
                    (left.nanosecondsOfSecond + right.nanosecondsOfSecond) / 2,
                )
            size = pasteDao.getSizeByTimeLessThan(mid)

            if (size >= minSize) {
                targetTime = mid
                right = RealmInstant.from(mid.epochSeconds - 1, mid.nanosecondsOfSecond)
            } else {
                left = RealmInstant.from(mid.epochSeconds + 1, mid.nanosecondsOfSecond)
            }
        }

        pasteDao.markDeleteByCleanTime(targetTime)
    }
}
