package com.clipevery.task

import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.TaskType
import com.clipevery.utils.TaskUtils.createFailExtraInfo
import io.github.oshai.kotlinlogging.KotlinLogging

class DeleteClipTaskExecutor(private val clipDao: ClipDao): SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override val taskType: Int = TaskType.DELETE_CLIP_TASK

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        try {
            clipDao.deleteClipData(clipTask.clipDataId!!)
            return SuccessClipTaskResult()
        } catch (e: Throwable) {
            logger.error(e) { "delete clip data error: $clipTask" }
            return FailureClipTaskResult(createFailExtraInfo(clipTask, e))
        }
    }
}