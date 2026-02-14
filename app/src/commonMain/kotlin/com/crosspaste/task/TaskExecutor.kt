package com.crosspaste.task

import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskDao
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.cpuDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch

class TaskExecutor(
    singleTypeTaskExecutors: List<SingleTypeTaskExecutor>,
    private val taskDao: TaskDao,
    maxConcurrentTasks: Int = 10,
    private val scope: CoroutineScope = CoroutineScope(cpuDispatcher + SupervisorJob()),
) {
    private val logger = KotlinLogging.logger {}

    private val singleTypeTaskExecutorMap = singleTypeTaskExecutors.associateBy { it.taskType }

    private val taskShardedFlow = MutableSharedFlow<Long>()

    private val executionSemaphore = Channel<Unit>(maxConcurrentTasks)

    init {
        scope.launch(CoroutineName("TaskExecutor")) {
            taskShardedFlow.collect { taskId ->
                executionSemaphore.send(Unit)
                launch {
                    try {
                        executeTask(taskId)
                    } finally {
                        executionSemaphore.receive()
                    }
                }
            }
        }
    }

    private fun getExecutorImpl(taskType: Int): SingleTypeTaskExecutor =
        singleTypeTaskExecutorMap[taskType] ?: throw IllegalArgumentException("Unknown task type: $taskType")

    private suspend fun executeTask(taskId: Long) {
        var currentTask: PasteTask? = null
        runCatching {
            taskDao.getTask(taskId)?.let { task ->
                currentTask = task
                taskDao.executingTask(taskId)
                val executor = getExecutorImpl(task.taskType)
                executor.executeTask(task, success = {
                    taskDao.successTask(taskId, it)
                }, fail = { pasteTaskExtraInfo, needRetry ->
                    taskDao.failureTask(taskId, needRetry, pasteTaskExtraInfo)
                }, retry = {
                    submitTask(taskId)
                })
            }
        }.onFailure { e ->
            logger.error(e) { "execute task error: $taskId" }
            currentTask?.let { task ->
                taskDao.failureTask(taskId, false, TaskUtils.createFailExtraInfo(task, e))
            }
        }
    }

    suspend fun submitTask(taskId: Long) {
        taskShardedFlow.emit(taskId)
    }

    suspend fun submitTasks(taskIds: List<Long>) {
        taskShardedFlow.emitAll(taskIds.asFlow())
    }

    fun shutdown() {
        scope.cancel()
        logger.info { "TaskExecutor shutdown complete" }
    }
}
