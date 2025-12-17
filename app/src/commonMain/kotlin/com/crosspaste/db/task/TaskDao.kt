package com.crosspaste.db.task

import com.crosspaste.Database
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext

class TaskDao(private val database: Database) {

    private val jsonUtils = getJsonUtils()

    private val pasteTaskDatabaseQueries = database.pasteTaskDatabaseQueries

    fun createTaskBlock(
        pasteDataId: Long?,
        taskType: Int,
        extraInfo: PasteTaskExtraInfo = BaseExtraInfo(),
    ): Long {
        val now = nowEpochMilliseconds()
        return database.transactionWithResult {
            pasteTaskDatabaseQueries.createTask(
                pasteDataId,
                taskType,
                TaskStatus.PREPARING,
                now,
                now,
                jsonUtils.JSON.encodeToString(extraInfo),
            )
            pasteTaskDatabaseQueries.getLastId().executeAsOne()
        }
    }

    suspend fun createTask(
        pasteDataId: Long?,
        taskType: Int,
        extraInfo: PasteTaskExtraInfo = BaseExtraInfo(),
    ): Long = withContext(ioDispatcher) {
        createTaskBlock(
            pasteDataId,
            taskType,
            extraInfo,
        )
    }

    suspend fun executingTask(taskId: Long) = withContext(ioDispatcher) {
        pasteTaskDatabaseQueries.executingTask(
            TaskStatus.EXECUTING,
            nowEpochMilliseconds(),
            taskId
        )
    }

    suspend fun successTask(taskId: Long, newExtraInfo: String?) = withContext(ioDispatcher) {
        newExtraInfo?.let {
            pasteTaskDatabaseQueries.finishTaskWithExtraInfo(
                TaskStatus.SUCCESS,
                nowEpochMilliseconds(),
                newExtraInfo,
                taskId
            )
        } ?: run {
            pasteTaskDatabaseQueries.finishTask(
                TaskStatus.SUCCESS,
                nowEpochMilliseconds(),
                taskId
            )
        }
    }

    suspend fun failureTask(taskId: Long, needRetry: Boolean, newExtraInfo: String?) = withContext(ioDispatcher) {
        newExtraInfo?.let {
            pasteTaskDatabaseQueries.finishTaskWithExtraInfo(
                if (needRetry) TaskStatus.PREPARING else TaskStatus.FAILURE,
                nowEpochMilliseconds(),
                newExtraInfo,
                taskId
            )
        } ?: run {
            pasteTaskDatabaseQueries.finishTask(
                if (needRetry) TaskStatus.PREPARING else TaskStatus.FAILURE,
                nowEpochMilliseconds(),
                taskId
            )
        }
    }

    suspend fun getTask(taskId: Long): PasteTask? = withContext(ioDispatcher) {
        pasteTaskDatabaseQueries.getTask(taskId).executeAsOneOrNull()?.let {
            PasteTask(
                taskId = it.taskId,
                pasteDataId = it.pasteDataId,
                taskType = it.taskType,
                status = it.status,
                createTime = it.createTime,
                modifyTime = it.modifyTime,
                extraInfo = it.extraInfo
            )
        }
    }

    suspend fun cleanSuccessTask(time: Long) = withContext(ioDispatcher) {
        pasteTaskDatabaseQueries.cleanSuccess(time)
    }

    suspend fun cleanFailureTask(time: Long) = withContext(ioDispatcher) {
        pasteTaskDatabaseQueries.cleanFail(time)
    }
}
