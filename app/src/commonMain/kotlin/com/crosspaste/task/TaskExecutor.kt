package com.crosspaste.task

import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskDao
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

    private suspend fun executeTask(taskId: Long) {
        var currentTask: PasteTask? = null
        runCatching {
            taskDao.getTask(taskId)?.let { task ->
                val executor = singleTypeTaskExecutorMap[task.taskType]
                if (executor == null) {
                    // A downgraded build can see task types persisted by a newer
                    // version. Leave the row untouched (instead of failing it
                    // terminally) so a future version can still recover it.
                    logger.warn { "No executor for task type ${task.taskType}, task $taskId left for future recovery" }
                    return
                }
                currentTask = task
                taskDao.executingTask(taskId)
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
                // Failure recording must itself be fault-tolerant: an exception
                // escaping here (e.g. corrupt extraInfo JSON) would cancel the
                // consumer coroutine and permanently stop task processing.
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

    /**
     * Re-enqueues tasks a previous process durably recorded but never finished.
     *
     * Must be called during startup after all task executors are registered.
     * [maxTaskId] must be captured (via [TaskDao.getMaxTaskId]) before any task
     * producer of the current process starts submitting work: task ids are
     * monotonic, so producer tasks always exceed the bound and are never
     * claimed — recovery cannot enqueue them twice. Runs at most once; the
     * claim itself is bounded, any overflow is claimed on a later startup.
     */
    suspend fun recoverPersistedTasks(maxTaskId: Long) {
        if (!recoveryStarted.compareAndSet(expect = false, update = true)) {
            return
        }
        runCatching {
            val taskIds = taskDao.claimRecoverableTasks(maxTaskId)
            if (taskIds.isEmpty()) {
                logger.info { "No persisted tasks to recover" }
            } else {
                taskIds.forEach { submitTask(it) }
                logger.info {
                    "Recovered ${taskIds.size} persisted tasks, first ids: ${taskIds.take(20)}"
                }
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
        try {
            val drained = withTimeoutOrNull(drainTimeout) { consumerJob.join() } != null
            logger.info { "TaskExecutor shutdown complete (drained=$drained)" }
        } finally {
            // Must run even when an enclosing shutdown timeout cancels the
            // drain, otherwise task coroutines outlive the closed database.
            scope.cancel()
        }
    }
}
