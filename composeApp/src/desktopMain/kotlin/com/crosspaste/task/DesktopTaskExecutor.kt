package com.crosspaste.task

import com.crosspaste.dao.task.PasteTaskDao
import com.crosspaste.dao.task.TaskStatus
import com.crosspaste.utils.DesktopTaskUtils.createFailExtraInfo
import com.crosspaste.utils.cpuDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import org.mongodb.kbson.ObjectId

class DesktopTaskExecutor(
    singleTypeTaskExecutors: List<SingleTypeTaskExecutor>,
    private val pasteTaskDao: PasteTaskDao,
) : TaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val singleTypeTaskExecutorMap = singleTypeTaskExecutors.associateBy { it.taskType }

    private val taskShardedFlow = MutableSharedFlow<ObjectId>()

    private val scope = CoroutineScope(cpuDispatcher + SupervisorJob())

    init {
        scope.launch(CoroutineName("TaskExecutor")) {
            taskShardedFlow.collect { taskId ->
                launch {
                    executeTask(taskId)
                }
            }
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
            pasteTaskDao.update(taskId, copeFromRealm = true) {
                status = TaskStatus.EXECUTING
                modifyTime = System.currentTimeMillis()
            }?.let { pasteTask ->
                val executor = getExecutorImpl(pasteTask.taskType)
                executor.executeTask(pasteTask, success = {
                    pasteTaskDao.update(taskId) {
                        status = TaskStatus.SUCCESS
                        modifyTime = System.currentTimeMillis()
                        it?.let { newExtraInfo ->
                            extraInfo = newExtraInfo
                        }
                    }
                }, fail = { pasteTaskExtraInfo, needRetry ->
                    pasteTaskDao.update(taskId) {
                        status = if (needRetry) TaskStatus.PREPARING else TaskStatus.FAILURE
                        modifyTime = System.currentTimeMillis()
                        extraInfo = pasteTaskExtraInfo
                    }
                }, retry = {
                    submitTask(taskId)
                })
            }
        } catch (e: Throwable) {
            logger.error(e) { "execute task error: $taskId" }
            pasteTaskDao.update(taskId) {
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
