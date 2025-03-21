package com.crosspaste.task

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.utils.TaskUtils
import io.github.oshai.kotlinlogging.KotlinLogging

class DeletePasteTaskExecutor(private val pasteDao: PasteDao) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override val taskType: Int = TaskType.DELETE_PASTE_TASK

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        return runCatching {
            pasteDao.deletePasteData(pasteTask.pasteDataId!!)
            SuccessPasteTaskResult()
        }.getOrElse {
            TaskUtils.createFailurePasteTaskResult(
                logger = logger,
                retryHandler = { false },
                startTime = pasteTask.modifyTime,
                fails = listOf(createFailureResult(StandardErrorCode.DELETE_TASK_FAIL, it)),
                extraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class),
            )
        }
    }
}
