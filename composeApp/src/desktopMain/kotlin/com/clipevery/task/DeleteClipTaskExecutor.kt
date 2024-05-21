package com.clipevery.task

import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.TaskType
import com.clipevery.exception.StandardErrorCode
import com.clipevery.net.clientapi.createFailureResult
import com.clipevery.task.extra.BaseExtraInfo
import com.clipevery.utils.TaskUtils
import com.clipevery.utils.TaskUtils.createFailureClipTaskResult
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
