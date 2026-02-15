package com.crosspaste.task

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.DelayedDeleteExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskType
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.TaskUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay

class DelayedDeletePasteTaskExecutor(
    private val pasteDao: PasteDao,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override val taskType: Int = TaskType.DELAYED_DELETE_PASTE_TASK

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult =
        runCatching {
            val extraInfo = TaskUtils.getExtraInfo(pasteTask, DelayedDeleteExtraInfo::class)
            val remaining = extraInfo.executeAfter - nowEpochMilliseconds()
            if (remaining > 0) {
                delay(remaining)
            }
            pasteTask.pasteDataId?.let { pasteDataId ->
                pasteDao.deletePasteData(pasteDataId)
            }
            SuccessPasteTaskResult()
        }.getOrElse {
            logger.error(it) { "Delayed delete paste task failed" }
            SuccessPasteTaskResult()
        }
}
