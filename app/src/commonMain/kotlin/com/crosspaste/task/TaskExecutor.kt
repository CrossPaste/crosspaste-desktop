package com.crosspaste.task

import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskDao
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.cpuDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.cancellation.CancellationException

class TaskExecutor(
    singleTypeTaskExecutors: List<SingleTypeTaskExecutor>,
    private val taskDao: TaskDao,
    maxConcurrentTasks: Int = 10,
    private val scope: CoroutineScope = namedScope(cpuDispatcher, "TaskExecutor"),
) {
    private val logger = KotlinLogging.logger {}

    private val singleTypeTaskExecutorMap = singleTypeTaskExecutors.associateBy { it.taskType }

    private val taskChannel = Channel<Long>(Channel.UNLIMITED)

    private val semaphore = Semaphore(maxConcurrentTasks)

    init {
        scope.launch(CoroutineName("TaskExecutor")) {
            for (taskId in taskChannel) {
                semaphore.acquire()
                launch {
                    try {
                        executeTask(taskId)
                    } finally {
                        semaphore.release()
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
            // A cancelled execution (shutdown) must not be recorded as a
            // terminal FAILURE: rethrow so the row keeps its persisted status.
            if (e is CancellationException) throw e
            logger.error(e) { "execute task error: $taskId" }
            currentTask?.let { task ->
                // Failure recording must itself be fault-tolerant: an exception
                // escaping here (e.g. corrupt extraInfo JSON in
                // createFailExtraInfo) would cancel the consumer coroutine and
                // permanently stop task processing for the whole session.
                val failExtraInfo = runCatching { TaskUtils.createFailExtraInfo(task, e) }.getOrNull()
                runCatching {
                    taskDao.failureTask(taskId, false, failExtraInfo)
                }.onFailure { persistError ->
                    if (persistError is CancellationException) throw persistError
                    logger.error(persistError) { "record task failure error: $taskId" }
                }
            }
        }
    }

    suspend fun submitTask(taskId: Long) {
        if (taskChannel.trySend(taskId).isFailure) {
            // The channel is only closed during shutdown; a producer racing it
            // must not crash — the unfinished row just stays persisted.
            logger.warn { "Task channel closed, dropping submission of task $taskId" }
        }
    }

    suspend fun submitTasks(taskIds: List<Long>) {
        taskIds.forEach { submitTask(it) }
    }

    fun shutdown() {
        taskChannel.close()
        scope.cancel()
        logger.info { "TaskExecutor shutdown complete" }
    }
}
