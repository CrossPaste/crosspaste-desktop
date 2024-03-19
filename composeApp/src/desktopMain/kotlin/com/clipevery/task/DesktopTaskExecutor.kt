package com.clipevery.task

import com.clipevery.dao.task.ClipTaskDao
import com.clipevery.dao.task.TaskStatus
import com.clipevery.utils.TaskUtils.createFailExtraInfo
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
            clipTaskDao.update(taskId, copeFromRealm = true) {
                status = TaskStatus.EXECUTING
                modifyTime = System.currentTimeMillis()
            }?.let { clipTask ->
                val executor = getExecutorImpl(clipTask.taskType)
                executor.executeTask(clipTask, success = {
                    clipTaskDao.update(taskId) {
                        status = TaskStatus.SUCCESS
                        modifyTime = System.currentTimeMillis()
                        it?.let {  newExtraInfo ->
                            extraInfo = newExtraInfo
                        }
                    }
                }, fail = { clipTaskExtraInfo, needRetry ->
                    clipTaskDao.update(taskId) {
                        status = if (needRetry) TaskStatus.PREPARING else TaskStatus.FAILURE
                        modifyTime = System.currentTimeMillis()
                        extraInfo = clipTaskExtraInfo
                    }
                }, retry = {
                    submitTask(taskId)
                })
            }
        } catch (e: Throwable) {
            logger.error(e) { "execute task error: $taskId" }
            clipTaskDao.update(taskId) {
                status = TaskStatus.FAILURE
                extraInfo = createFailExtraInfo(this, e)
            }
        }
    }

    override suspend fun submitTask(taskId: ObjectId) {
        taskShardedFlow.emit(taskId)
    }

    override suspend fun submitTasks(taskIds: List<ObjectId>) {
        taskShardedFlow.emitAll(taskIds.asFlow())
    }
}