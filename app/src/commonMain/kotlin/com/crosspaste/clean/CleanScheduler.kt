package com.crosspaste.clean

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.task.TaskDao
import com.crosspaste.db.task.TaskType
import com.crosspaste.task.TaskExecutor
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class CleanScheduler(
    private val taskDao: TaskDao,
    private val taskExecutor: TaskExecutor,
    private val configManager: CommonConfigManager,
) {

    private val logger = KotlinLogging.logger {}

    private val coroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val cleanInterval = 5.minutes

    fun start() {
        coroutineScope.launch {
            logger.info { "CleanScheduler started" }

            while (isActive) {
                try {
                    launch { safeCleanPaste() }
                    launch { safeCleanTask() }
                    delay(cleanInterval)
                } catch (e: Exception) {
                    logger.error(e) { "Unexpected error in clean scheduler main loop" }
                    delay(1.minutes)
                }
            }

            logger.info { "CleanScheduler stopped" }
        }
    }

    private suspend fun safeCleanPaste() {
        runCatching {
            if (configManager.getCurrentConfig().enableExpirationCleanup) {
                cleanPaste()
            }
        }.onFailure { e ->
            logger.error(e) { "Error during paste cleanup" }
        }
    }

    private suspend fun cleanPaste() {
        val taskId =
            taskDao.createTask(
                pasteDataId = null,
                taskType = TaskType.CLEAN_PASTE_TASK,
            )
        taskExecutor.submitTask(taskId)
        logger.info { "Submitted clean paste task: $taskId" }
    }

    private suspend fun safeCleanTask() {
        runCatching {
            cleanTask()
        }.onFailure { e ->
            logger.error(e) { "Error during task cleanup" }
        }
    }

    private suspend fun cleanTask() {
        val taskId =
            taskDao.createTask(
                pasteDataId = null,
                taskType = TaskType.CLEAN_TASK_TASK,
            )
        taskExecutor.submitTask(taskId)
        logger.info { "Submitted clean task task: $taskId" }
    }

    fun stop() {
        coroutineScope.cancel()
    }
}
