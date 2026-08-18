package com.crosspaste.task

import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskDao
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.cpuDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TaskExecutor(
    singleTypeTaskExecutors: List<SingleTypeTaskExecutor>,
    private val taskDao: TaskDao,
    maxConcurrentTasks: Int = 10,
    private val drainTimeout: Duration = 2.seconds,
    private val scope: CoroutineScope = namedScope(cpuDispatcher, "TaskExecutor"),
) {
    private val logger = KotlinLogging.logger {}

    private val singleTypeTaskExecutorMap = singleTypeTaskExecutors.associateBy { it.taskType }

    private val taskChannel = Channel<Long>(Channel.UNLIMITED)

    private val semaphore = Semaphore(maxConcurrentTasks)

    private val recoveryStarted = atomic(false)

    private val consumerJob =
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
            // A cancelled execution must not be recorded as a terminal FAILURE:
            // the row keeps its persisted status and startup recovery resumes it.
            if (e is CancellationException) throw e
            logger.error(e) { "execute task error: $taskId" }
            currentTask?.let { task ->
                taskDao.failureTask(taskId, false, TaskUtils.createFailExtraInfo(task, e))
            }
        }
    }

    /**
     * Re-enqueues tasks a previous process durably recorded but never finished.
     *
     * Must be called during startup after all task executors are registered.
     * [createdBefore] must be captured before any task producer of the current
     * process starts submitting work: producer tasks then have
     * createTime >= the bound and are never claimed, so recovery cannot
     * enqueue them twice. Runs at most once.
     */
    suspend fun recoverPersistedTasks(createdBefore: Long = nowEpochMilliseconds()) {
        if (!recoveryStarted.compareAndSet(expect = false, update = true)) {
            return
        }
        runCatching {
            val taskIds = taskDao.claimRecoverableTasks(createdBefore)
            if (taskIds.isEmpty()) {
                logger.info { "No persisted tasks to recover" }
            } else {
                taskIds.forEach { submitTask(it) }
                logger.info { "Recovered ${taskIds.size} persisted tasks: $taskIds" }
            }
        }.onFailure { e ->
            logger.error(e) { "Failed to recover persisted tasks" }
        }
    }

    suspend fun submitTask(taskId: Long) {
        if (taskChannel.trySend(taskId).isFailure) {
            // Shutdown already closed the channel: the task row stays PREPARING
            // and is picked up by recoverPersistedTasks on the next start.
            logger.warn { "Task channel closed, task $taskId deferred to next-start recovery" }
        }
    }

    suspend fun submitTasks(taskIds: List<Long>) {
        taskIds.forEach { submitTask(it) }
    }

    /**
     * Stops accepting new tasks, drains already-queued and in-flight tasks for
     * up to [drainTimeout], then cancels whatever is still running. Cancelled
     * tasks keep their persisted status and are resumed by
     * [recoverPersistedTasks] on the next start.
     */
    suspend fun shutdown() {
        taskChannel.close()
        val drained = withTimeoutOrNull(drainTimeout) { consumerJob.join() } != null
        scope.cancel()
        logger.info { "TaskExecutor shutdown complete (drained=$drained)" }
    }
}
