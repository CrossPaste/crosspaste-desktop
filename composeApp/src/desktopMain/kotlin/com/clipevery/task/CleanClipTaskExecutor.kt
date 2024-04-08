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
        if (configManager.config.isAutoCleaning) {
            try {
                return cleanLock.withLock {
                    val imageCleanTime = CleanTime.entries[configManager.config.imageCleanTimeIndex]
                    val imageCleanTimeInstant = DateUtils.getRealmInstant(-imageCleanTime.days)
                    clipDao.getMarkDeleteByCleanTime(imageCleanTimeInstant, ClipType.IMAGE)

                    val fileCleanTime = CleanTime.entries[configManager.config.fileCleanTimeIndex]
                    val fileCleanTimeInstant = DateUtils.getRealmInstant(-fileCleanTime.days)
                    clipDao.getMarkDeleteByCleanTime(fileCleanTimeInstant, ClipType.FILE)
                    return@withLock SuccessClipTaskResult()
                }
            } catch (e: Throwable) {
                return createFailureClipTaskResult(
                    retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                    startTime = clipTask.modifyTime,
                    failMessage = e.message ?: "Failed to clean clip data",
                    extraInfo = baseExtraInfo,
                )
            }
        } else {
            return SuccessClipTaskResult()
        }
    }
}
