package com.clipevery.task

import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.ClipTaskExtraInfo

interface SingleTypeTaskExecutor {

    suspend fun executeTask(clipTask: ClipTask,
                            success: suspend (ClipTaskExtraInfo?) -> Unit,
                            fail: suspend (ClipTaskExtraInfo, Boolean) -> Unit,
                            retry: suspend () -> Unit) {
        val result = doExecuteTask(clipTask)

        if (result is SuccessClipTaskResult) {
            success(result.newExtraInfo)
        } else {
            val newExtraInfo = (result as FailClipTaskResult).newExtraInfo
            val needRetry = needRetry(clipTask, newExtraInfo)
            fail(newExtraInfo, needRetry)
            if (needRetry) {
                retry()
            }
        }
    }

    suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult

    fun needRetry(clipTask: ClipTask, newExtraInfo: ClipTaskExtraInfo): Boolean
}

interface ClipTaskResult

data class SuccessClipTaskResult(val newExtraInfo: ClipTaskExtraInfo? = null): ClipTaskResult

data class FailClipTaskResult(val newExtraInfo: ClipTaskExtraInfo): ClipTaskResult