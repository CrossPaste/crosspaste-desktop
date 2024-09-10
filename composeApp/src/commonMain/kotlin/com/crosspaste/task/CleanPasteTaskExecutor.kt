package com.crosspaste.task

import com.crosspaste.clean.CleanTime
import com.crosspaste.config.ConfigManager
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.realm.task.TaskType
import com.crosspaste.task.extra.BaseExtraInfo
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.getDateUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CleanPasteTaskExecutor(
    private val pasteRealm: PasteRealm,
    private val configManager: ConfigManager,
) : SingleTypeTaskExecutor {

    private val logger: KLogger = KotlinLogging.logger {}

    private val dateUtils = getDateUtils()

    override val taskType: Int = TaskType.CLEAN_PASTE_TASK

    private val cleanLock = Mutex()

    override suspend fun doExecuteTask(pasteTask: com.crosspaste.realm.task.PasteTask): PasteTaskResult {
        if (configManager.config.isThresholdCleanup) {
            try {
                cleanLock.withLock {
                    val imageCleanTime = CleanTime.entries[configManager.config.imageCleanTimeIndex]
                    val imageCleanTimeInstant = dateUtils.getRealmInstant(-imageCleanTime.days)
                    pasteRealm.markDeleteByCleanTime(imageCleanTimeInstant, PasteType.IMAGE)

                    val fileCleanTime = CleanTime.entries[configManager.config.fileCleanTimeIndex]
                    val fileCleanTimeInstant = dateUtils.getRealmInstant(-fileCleanTime.days)
                    pasteRealm.markDeleteByCleanTime(fileCleanTimeInstant, PasteType.FILE)
                }
            } catch (e: Throwable) {
                val baseExtraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class)
                return TaskUtils.createFailurePasteTaskResult(
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
                    val allSize = pasteRealm.getSize(true)
                    val favoriteSize = pasteRealm.getSize(false)
                    val noFavoriteSize = allSize - favoriteSize
                    if (noFavoriteSize > configManager.config.maxStorage * 1024 * 1024) {
                        val cleanSize = noFavoriteSize * configManager.config.cleanupPercentage / 100

                        deleteStorageOfApproximateSize(cleanSize, noFavoriteSize)
                    }
                }
            } catch (e: Throwable) {
                val baseExtraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class)
                return TaskUtils.createFailurePasteTaskResult(
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
        // If there's no data, return immediately
        val minTime = pasteRealm.getMinPasteDataCreateTime() ?: return
        val currentTime = RealmInstant.now()

        val proportion = minSize.toDouble() / totalSize
        val estimatedDuration = currentTime.epochSeconds - minTime.epochSeconds
        val targetDuration = (estimatedDuration * proportion).toLong()
        val estimatedTargetTime = RealmInstant.from(minTime.epochSeconds + targetDuration, minTime.nanosecondsOfSecond)

        var left = minTime
        var right = currentTime
        var targetTime = estimatedTargetTime

        var size = pasteRealm.getSizeByTimeLessThan(targetTime)
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
            size = pasteRealm.getSizeByTimeLessThan(mid)

            if (size >= minSize) {
                targetTime = mid
                right = RealmInstant.from(mid.epochSeconds - 1, mid.nanosecondsOfSecond)
            } else {
                left = RealmInstant.from(mid.epochSeconds + 1, mid.nanosecondsOfSecond)
            }
        }

        pasteRealm.markDeleteByCleanTime(targetTime)
    }
}
