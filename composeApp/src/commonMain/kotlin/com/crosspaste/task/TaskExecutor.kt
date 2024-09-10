package com.crosspaste.task

import com.crosspaste.realm.task.TaskStatus
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.cpuDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.mongodb.kbson.ObjectId

class TaskExecutor(
    singleTypeTaskExecutors: List<SingleTypeTaskExecutor>,
    private val pasteTaskRealm: com.crosspaste.realm.task.PasteTaskRealm,
) {
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
            pasteTaskRealm.update(taskId, copeFromRealm = true) {
                status = TaskStatus.EXECUTING
                modifyTime = Clock.System.now().toEpochMilliseconds()
            }?.let { pasteTask ->
                val executor = getExecutorImpl(pasteTask.taskType)
                executor.executeTask(pasteTask, success = {
                    pasteTaskRealm.update(taskId) {
                        status = TaskStatus.SUCCESS
                        modifyTime = Clock.System.now().toEpochMilliseconds()
                        it?.let { newExtraInfo ->
                            extraInfo = newExtraInfo
                        }
                    }
                }, fail = { pasteTaskExtraInfo, needRetry ->
                    pasteTaskRealm.update(taskId) {
                        status = if (needRetry) TaskStatus.PREPARING else TaskStatus.FAILURE
                        modifyTime = Clock.System.now().toEpochMilliseconds()
                        extraInfo = pasteTaskExtraInfo
                    }
                }, retry = {
                    submitTask(taskId)
                })
            }
        } catch (e: Throwable) {
            logger.error(e) { "execute task error: $taskId" }
            pasteTaskRealm.update(taskId) {
                status = TaskStatus.FAILURE
                extraInfo = TaskUtils.createFailExtraInfo(this, e)
            }
        }
    }

    suspend fun submitTask(taskId: ObjectId) {
        taskShardedFlow.emit(taskId)
    }

    suspend fun submitTasks(taskIds: List<ObjectId>) {
        taskShardedFlow.emitAll(taskIds.asFlow())
    }
}
