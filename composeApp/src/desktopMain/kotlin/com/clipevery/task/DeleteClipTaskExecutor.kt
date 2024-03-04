package com.clipevery.task

import com.clipevery.dao.task.ClipTask
import io.github.oshai.kotlinlogging.KotlinLogging

class DeleteClipTaskExecutor: SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override fun executeTask(clipTask: ClipTask) {
        logger.info { "execute task: $clipTask" }
    }

    override fun needRetry(clipTask: ClipTask): Boolean {
        return false
    }
}