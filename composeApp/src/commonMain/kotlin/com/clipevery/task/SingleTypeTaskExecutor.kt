package com.clipevery.task

import com.clipevery.dao.task.ClipTask

interface SingleTypeTaskExecutor {

    suspend fun executeTask(clipTask: ClipTask,
                            success: suspend (String?) -> Unit,
                            fail: suspend (String, Boolean) -> Unit,
                            retry: suspend () -> Unit) {
        val result = doExecuteTask(clipTask)

        if (result is SuccessClipTaskResult) {
            success(result.newExtraInfo)
        } else {
            val failClipTaskResult = result as FailClipTaskResult
            val newExtraInfo = failClipTaskResult.newExtraInfo
            val needRetry = failClipTaskResult.needRetry
            fail(newExtraInfo, needRetry)
            if (needRetry) {
                retry()
            }
        }
    }

    suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult
}

interface ClipTaskResult

data class SuccessClipTaskResult(val newExtraInfo: String? = null): ClipTaskResult

data class FailClipTaskResult(val newExtraInfo: String, val needRetry: Boolean = false): ClipTaskResult