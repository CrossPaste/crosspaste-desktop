package com.crosspaste.task

import com.crosspaste.db.task.PasteTask

interface SingleTypeTaskExecutor {

    val taskType: Int

    suspend fun executeTask(
        pasteTask: PasteTask,
        success: suspend (String?) -> Unit,
        fail: suspend (String, Boolean) -> Unit,
        retry: suspend () -> Unit,
    ) {
        val result = doExecuteTask(pasteTask)

        if (result is SuccessPasteTaskResult) {
            success(result.newExtraInfo)
        } else {
            val failurePasteTaskResult = result as FailurePasteTaskResult
            val newExtraInfo = failurePasteTaskResult.newExtraInfo
            val needRetry = failurePasteTaskResult.needRetry
            fail(newExtraInfo, needRetry)
            if (needRetry) {
                retry()
            }
        }
    }

    suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult
}

interface PasteTaskResult

data class SuccessPasteTaskResult(val newExtraInfo: String? = null) : PasteTaskResult

data class FailurePasteTaskResult(val newExtraInfo: String, val needRetry: Boolean = false) : PasteTaskResult
