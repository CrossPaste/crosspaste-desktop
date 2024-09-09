package com.crosspaste.task

import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.task.PasteTask
import com.crosspaste.dao.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.task.extra.BaseExtraInfo
import com.crosspaste.utils.getTaskUtils
import io.github.oshai.kotlinlogging.KotlinLogging

class DeletePasteTaskExecutor(private val pasteDao: PasteDao) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val taskUtils = getTaskUtils()

    override val taskType: Int = TaskType.DELETE_PASTE_TASK

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        try {
            pasteDao.deletePasteData(pasteTask.pasteDataId!!)
            return SuccessPasteTaskResult()
        } catch (e: Throwable) {
            return taskUtils.createFailurePasteTaskResult(
                logger = logger,
                retryHandler = { false },
                startTime = pasteTask.modifyTime,
                fails = listOf(createFailureResult(StandardErrorCode.DELETE_TASK_FAIL, e)),
                extraInfo = taskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class),
            )
        }
    }
}
