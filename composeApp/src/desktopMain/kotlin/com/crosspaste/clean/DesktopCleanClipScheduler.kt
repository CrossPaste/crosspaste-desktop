package com.crosspaste.clean

import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.task.ClipTaskDao
import com.crosspaste.dao.task.TaskType
import com.crosspaste.task.TaskExecutor
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.cpuDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DesktopCleanClipScheduler(
    private val taskDao: ClipTaskDao,
    private val taskExecutor: TaskExecutor,
    private val configManager: ConfigManager,
) : CleanClipScheduler {

    private val logger = KotlinLogging.logger {}

    private val coroutineScope = CoroutineScope(cpuDispatcher)

    override fun start() {
        coroutineScope.launch {
            while (isActive) {
                if (configManager.config.isExpirationCleanup) {
                    val taskId = taskDao.createTask(TaskUtils.createTask(null, TaskType.CLEAN_CLIP_TASK))
                    taskExecutor.submitTask(taskId)
                    logger.info { "submit clean clip task: $taskId" }
                }
                delay(5 * 60 * 1000)
            }
        }
    }

    override fun stop() {
        coroutineScope.cancel()
    }
}
