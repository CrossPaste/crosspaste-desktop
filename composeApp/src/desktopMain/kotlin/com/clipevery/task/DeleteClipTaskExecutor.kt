package com.clipevery.task

import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.ClipTaskExtraInfo
import io.github.oshai.kotlinlogging.KotlinLogging

class DeleteClipTaskExecutor: SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        logger.info { "execute task: $clipTask" }
        return SuccessClipTaskResult()
    }

    override fun needRetry(clipTask: ClipTask, newExtraInfo: ClipTaskExtraInfo): Boolean {
        return false
    }
}