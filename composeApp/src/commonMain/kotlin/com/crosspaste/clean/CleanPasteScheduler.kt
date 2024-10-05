package com.crosspaste.clean

import com.crosspaste.config.ConfigManager
import com.crosspaste.realm.task.PasteTaskRealm
import com.crosspaste.realm.task.TaskType
import com.crosspaste.task.TaskExecutor
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.cpuDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CleanPasteScheduler(
    private val taskDao: PasteTaskRealm,
    private val taskExecutor: TaskExecutor,
    private val configManager: ConfigManager,
) {

    private val logger = KotlinLogging.logger {}

    private val coroutineScope = CoroutineScope(cpuDispatcher)

    fun start() {
        coroutineScope.launch {
            while (isActive) {
                if (configManager.config.enableExpirationCleanup) {
                    val taskId = taskDao.createTask(TaskUtils.createTask(null, TaskType.CLEAN_PASTE_TASK))
                    taskExecutor.submitTask(taskId)
                    logger.info { "submit clean paste task: $taskId" }
                }
                delay(5 * 60 * 1000)
            }
        }
    }

    fun stop() {
        coroutineScope.cancel()
    }
}
