package com.clipevery.task

import com.clipevery.clean.CleanTime
import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipType
import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.TaskType
import com.clipevery.task.extra.BaseExtraInfo
import com.clipevery.utils.DateUtils
import com.clipevery.utils.TaskUtils
import com.clipevery.utils.TaskUtils.createFailureClipTaskResult
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CleanClipTaskExecutor(
    private val clipDao: ClipDao,
    private val configManager: ConfigManager,
) : SingleTypeTaskExecutor {

    override val taskType: Int = TaskType.CLEAN_CLIP_TASK

    private val cleanLock = Mutex()

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        val baseExtraInfo = TaskUtils.getExtraInfo(clipTask, BaseExtraInfo::class)
        if (configManager.config.isThresholdCleanup) {
            try {
                cleanLock.withLock {
                    val imageCleanTime = CleanTime.entries[configManager.config.imageCleanTimeIndex]
                    val imageCleanTimeInstant = DateUtils.getRealmInstant(-imageCleanTime.days)
                    clipDao.markDeleteByCleanTime(imageCleanTimeInstant, ClipType.IMAGE)

                    val fileCleanTime = CleanTime.entries[configManager.config.fileCleanTimeIndex]
                    val fileCleanTimeInstant = DateUtils.getRealmInstant(-fileCleanTime.days)
                    clipDao.markDeleteByCleanTime(fileCleanTimeInstant, ClipType.FILE)
                }
            } catch (e: Throwable) {
                return createFailureClipTaskResult(
                    retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                    startTime = clipTask.modifyTime,
                    failMessage = e.message ?: "Failed to clean clip data",
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
                return createFailureClipTaskResult(
                    retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                    startTime = clipTask.modifyTime,
                    failMessage = e.message ?: "Failed to clean clip data",
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
