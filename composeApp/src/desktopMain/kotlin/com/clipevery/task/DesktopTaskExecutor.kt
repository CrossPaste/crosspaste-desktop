package com.clipevery.task

import com.clipevery.dao.task.ClipTaskDao
import com.clipevery.dao.task.ExecutionHistory
import com.clipevery.dao.task.TaskStatus
import com.clipevery.task.extra.BaseExtraInfo
import com.clipevery.utils.JsonUtils
import com.clipevery.utils.cpuDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
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
                    clipTaskDao.update(taskId) {
                        status = TaskStatus.EXECUTING
                        modifyTime = System.currentTimeMillis()
                    }
                    executor.executeTask(clipTask, success = {
                        clipTaskDao.update(taskId) {
                            status = TaskStatus.SUCCESS
                            modifyTime = System.currentTimeMillis()
                            it?.let {  newExtraInfo ->
                                extraInfo = JsonUtils.JSON.encodeToString(newExtraInfo)
                            }
                        }
                    }, fail = {
                        clipTaskDao.update(taskId) {
                            status = TaskStatus.FAILURE
                            modifyTime = System.currentTimeMillis()
                            extraInfo = JsonUtils.JSON.encodeToString(it)
                        }
                    }, retry = {
                        submitTask(taskId)
                    })
                } catch (e: Throwable) {
                    logger.error(e) { "execute task failed: ${clipTask.taskId}" }
                    clipTaskDao.update(taskId) {
                        status = TaskStatus.FAILURE
                        val currentTime = System.currentTimeMillis()
                        val executionHistory = ExecutionHistory(startTime = modifyTime,
                            endTime = currentTime,
                            status = TaskStatus.FAILURE,
                            message = e.message ?: "Unknown error")
                        val clipTaskExtraInfo = JsonUtils.JSON.decodeFromString<BaseExtraInfo>(extraInfo)
                        clipTaskExtraInfo.executionHistories.add(executionHistory)
                        extraInfo = JsonUtils.JSON.encodeToString(clipTaskExtraInfo)
                    }
                }
            }
        } catch (e: Throwable) {
            logger.error(e) { "execute task fail: $taskId" }
        }
    }

    override suspend fun submitTask(taskId: ObjectId) {
        taskShardedFlow.emit(taskId)
    }

    override suspend fun submitTasks(taskIds: List<ObjectId>) {
        taskShardedFlow.emitAll(taskIds.asFlow())
    }
}