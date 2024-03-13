package com.clipevery.task

import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.task.ClipTask
import com.clipevery.utils.TaskUtils.createFailExtraInfo
import io.github.oshai.kotlinlogging.KotlinLogging

class DeleteClipTaskExecutor(private val lazyClipDao: Lazy<ClipDao>): SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val clipDao: ClipDao by lazy { lazyClipDao.value }

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        try {
            clipDao.deleteClipData(clipTask.clipId)
            return SuccessClipTaskResult()
        } catch (e: Throwable) {
            logger.error(e) { "delete clip data error: $clipTask" }
            return FailClipTaskResult(createFailExtraInfo(clipTask, e))
        }
    }
}