package com.crosspaste.task

import com.crosspaste.clean.CleanTime
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.clip.ClipDao
import com.crosspaste.dao.clip.ClipType
import com.crosspaste.dao.task.ClipTask
import com.crosspaste.dao.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.task.extra.BaseExtraInfo
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.TaskUtils.createFailureClipTaskResult
import com.crosspaste.utils.getDateUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CleanClipTaskExecutor(
    private val clipDao: ClipDao,
    private val configManager: ConfigManager,
) : SingleTypeTaskExecutor {

    private val logger: KLogger = KotlinLogging.logger {}

    private val dateUtils: DateUtils = getDateUtils()

    override val taskType: Int = TaskType.CLEAN_CLIP_TASK

    private val cleanLock = Mutex()

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        if (configManager.config.isThresholdCleanup) {
            try {
                cleanLock.withLock {
                    val imageCleanTime = CleanTime.entries[configManager.config.imageCleanTimeIndex]
                    val imageCleanTimeInstant = dateUtils.getRealmInstant(-imageCleanTime.days)
                    clipDao.markDeleteByCleanTime(imageCleanTimeInstant, ClipType.IMAGE)

                    val fileCleanTime = CleanTime.entries[configManager.config.fileCleanTimeIndex]
                    val fileCleanTimeInstant = dateUtils.getRealmInstant(-fileCleanTime.days)
                    clipDao.markDeleteByCleanTime(fileCleanTimeInstant, ClipType.FILE)
                }
            } catch (e: Throwable) {
                val baseExtraInfo = TaskUtils.getExtraInfo(clipTask, BaseExtraInfo::class)
                return createFailureClipTaskResult(
                    logger = logger,
                    retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                    startTime = clipTask.modifyTime,
                    fails = listOf(createFailureResult(StandardErrorCode.CLEAN_TASK_FAIL, e)),
                    extraInfo = baseExtraInfo,
                )
            }
        }

        if (configManager.config.isThresholdCleanup) {
            try {
                cleanLock.withLock {
                    val allSize = clipDao.getSize(true)
                    val favoriteSize = clipDao.getSize(false)
                    val noFavoriteSize = allSize - favoriteSize
                    if (noFavoriteSize > configManager.config.maxStorage * 1024 * 1024) {
                        val cleanSize = noFavoriteSize * configManager.config.cleanupPercentage / 100

                        deleteStorageOfApproximateSize(cleanSize, noFavoriteSize)
                    }
                }
            } catch (e: Throwable) {
                val baseExtraInfo = TaskUtils.getExtraInfo(clipTask, BaseExtraInfo::class)
                return createFailureClipTaskResult(
                    logger = logger,
                    retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                    startTime = clipTask.modifyTime,
                    fails = listOf(createFailureResult(StandardErrorCode.CLEAN_TASK_FAIL, e)),
                    extraInfo = baseExtraInfo,
                )
            }
        }

        return SuccessClipTaskResult()
    }

    suspend fun deleteStorageOfApproximateSize(
        minSize: Long,
        totalSize: Long,
    ) {
        val minTime = clipDao.getMinClipDataCreateTime() ?: return // 如果没有数据，则直接返回
        val currentTime = RealmInstant.now()

        val proportion = minSize.toDouble() / totalSize
        val estimatedDuration = currentTime.epochSeconds - minTime.epochSeconds
        val targetDuration = (estimatedDuration * proportion).toLong()
        val estimatedTargetTime = RealmInstant.from(minTime.epochSeconds + targetDuration, minTime.nanosecondsOfSecond)

        var left = minTime
        var right = currentTime
        var targetTime = estimatedTargetTime

        var size = clipDao.getSizeByTimeLessThan(targetTime)
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
            size = clipDao.getSizeByTimeLessThan(mid)

            if (size >= minSize) {
                targetTime = mid
                right = RealmInstant.from(mid.epochSeconds - 1, mid.nanosecondsOfSecond)
            } else {
                left = RealmInstant.from(mid.epochSeconds + 1, mid.nanosecondsOfSecond)
            }
        }

        clipDao.markDeleteByCleanTime(targetTime)
    }
}
