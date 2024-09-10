package com.crosspaste.task

import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.realm.task.TaskType
import com.crosspaste.task.extra.BaseExtraInfo
import com.crosspaste.utils.TaskUtils
import io.github.oshai.kotlinlogging.KotlinLogging

class DeletePasteTaskExecutor(private val pasteRealm: PasteRealm) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override val taskType: Int = TaskType.DELETE_PASTE_TASK

    override suspend fun doExecuteTask(pasteTask: com.crosspaste.realm.task.PasteTask): PasteTaskResult {
        try {
            pasteRealm.deletePasteData(pasteTask.pasteDataId!!)
            return SuccessPasteTaskResult()
        } catch (e: Throwable) {
            return TaskUtils.createFailurePasteTaskResult(
                logger = logger,
                retryHandler = { false },
                startTime = pasteTask.modifyTime,
                fails = listOf(createFailureResult(StandardErrorCode.DELETE_TASK_FAIL, e)),
                extraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class),
            )
        }
    }
}
