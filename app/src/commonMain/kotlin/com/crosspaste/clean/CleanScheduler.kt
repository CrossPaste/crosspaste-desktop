package com.crosspaste.clean

import com.crosspaste.config.ConfigManager
import com.crosspaste.db.task.TaskDao
import com.crosspaste.db.task.TaskType
import com.crosspaste.task.TaskExecutor
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CleanScheduler(
    private val taskDao: TaskDao,
    private val taskExecutor: TaskExecutor,
    private val configManager: ConfigManager,
) {

    private val logger = KotlinLogging.logger {}

    private val coroutineScope = CoroutineScope(ioDispatcher)

    fun start() {
        coroutineScope.launch {
            while (isActive) {
                cleanPaste()
                cleanTask()
                delay(5 * 60 * 1000)
            }
        }
    }

    private suspend fun cleanPaste() {
        if (configManager.config.enableExpirationCleanup) {
            val taskId =
                taskDao.createTask(
                    pasteDataId = null,
                    taskType = TaskType.CLEAN_PASTE_TASK,
                )
            taskExecutor.submitTask(taskId)
            logger.info { "submit clean paste task: $taskId" }
        }
    }

    private suspend fun cleanTask() {
        val taskId =
            taskDao.createTask(
                pasteDataId = null,
                taskType = TaskType.CLEAN_TASK_TASK,
            )
        taskExecutor.submitTask(taskId)
        logger.info { "submit clean task task: $taskId" }
    }

    fun stop() {
        coroutineScope.cancel()
    }
}
