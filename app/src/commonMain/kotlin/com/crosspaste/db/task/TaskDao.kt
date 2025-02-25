package com.crosspaste.db.task

import com.crosspaste.Database
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.getJsonUtils

class TaskDao(private val database: Database) {

    private val jsonUtils = getJsonUtils()

    private val pasteTaskDatabaseQueries = database.pasteTaskDatabaseQueries

    fun createTask(
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
            ).executeAsOne()
        }
    }

    fun executingTask(taskId: Long) {
        pasteTaskDatabaseQueries.executingTask(
            TaskStatus.EXECUTING,
            nowEpochMilliseconds(),
            taskId
        )
    }

    fun successTask(taskId: Long, newExtraInfo: String?) {
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

    fun failureTask(taskId: Long, needRetry: Boolean, newExtraInfo: String?) {
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

    fun getTask(taskId: Long): PasteTask? {
        return pasteTaskDatabaseQueries.getTask(taskId).executeAsOneOrNull()?.let {
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
}
