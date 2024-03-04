package com.clipevery.task

import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.ClipTaskDao
import com.clipevery.utils.cpuDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import org.mongodb.kbson.ObjectId

class DesktopTaskExecutor(private val singleTypeTaskExecutorMap: Map<Int, SingleTypeTaskExecutor>,
                          private val clipTaskDao: ClipTaskDao): TaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val taskShardedFlow = MutableSharedFlow<ObjectId>()

    private val consumer = CoroutineScope(cpuDispatcher).launch {
        taskShardedFlow.collect { taskId ->
            executeTask(taskId)
        }
    }

    private fun getExecutorImpl(taskType: Int): SingleTypeTaskExecutor {
        singleTypeTaskExecutorMap[taskType]?.let {
            return it
        } ?: run {
            throw IllegalArgumentException("Unknown task type: $taskType")
        }
    }

    private suspend fun executeTask(taskId: ObjectId) {
        try {
            clipTaskDao.executingAndGet(taskId)?.let { clipTask ->
                val executor = getExecutorImpl(clipTask.taskType)
                try {
                    executor.executeTask(clipTask)
                    success(taskId)
                } catch (e: Throwable) {
                    logger.error(e) { "execute task failed: ${clipTask.taskId}" }
                    failAndGet(taskId, e)?.let { failClipTask ->
                        if (executor.needRetry(failClipTask)) {
                            reset(taskId)
                            submitTask(taskId)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logger.error(e) { "execute task fail: $taskId" }
        }
    }

    private suspend fun success(taskId: ObjectId) {
        clipTaskDao.success(taskId)
    }

    private suspend fun failAndGet(taskId: ObjectId, e: Throwable): ClipTask? {
        return clipTaskDao.failAndGet(taskId, e)
    }

    private suspend fun reset(taskId: ObjectId) {
        clipTaskDao.reset(taskId)
    }

    override suspend fun submitTask(taskId: ObjectId) {
        taskShardedFlow.emit(taskId)
    }

    override suspend fun submitTasks(taskIds: List<ObjectId>) {
        taskShardedFlow.emitAll(taskIds.asFlow())
    }
}