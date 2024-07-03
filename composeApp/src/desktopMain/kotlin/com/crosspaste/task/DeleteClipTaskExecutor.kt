package com.crosspaste.task

import com.crosspaste.dao.clip.ClipDao
import com.crosspaste.dao.task.ClipTask
import com.crosspaste.dao.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.task.extra.BaseExtraInfo
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.TaskUtils.createFailureClipTaskResult
import io.github.oshai.kotlinlogging.KotlinLogging

class DeleteClipTaskExecutor(private val clipDao: ClipDao) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override val taskType: Int = TaskType.DELETE_CLIP_TASK

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        try {
            clipDao.deleteClipData(clipTask.clipDataId!!)
            return SuccessClipTaskResult()
        } catch (e: Throwable) {
            return createFailureClipTaskResult(
                logger = logger,
                retryHandler = { false },
                startTime = clipTask.modifyTime,
                fails = listOf(createFailureResult(StandardErrorCode.DELETE_TASK_FAIL, e)),
                extraInfo = TaskUtils.getExtraInfo(clipTask, BaseExtraInfo::class),
            )
        }
    }
}
