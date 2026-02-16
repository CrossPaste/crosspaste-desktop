package com.crosspaste.task

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.DelayedDeleteExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskType
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.cpuDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DelayedDeletePasteTaskExecutor(
    private val pasteDao: PasteDao,
    private val scope: CoroutineScope = CoroutineScope(cpuDispatcher + SupervisorJob()),
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override val taskType: Int = TaskType.DELAYED_DELETE_PASTE_TASK

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        val extraInfo = TaskUtils.getExtraInfo(pasteTask, DelayedDeleteExtraInfo::class)
        val remaining = extraInfo.executeAfter - nowEpochMilliseconds()

        pasteTask.pasteDataId?.let { pasteDataId ->
            scope.launch {
                if (remaining > 0) {
                    delay(remaining)
                }
                runCatching {
                    pasteDao.deletePasteData(pasteDataId)
                }.onFailure { e ->
                    logger.error(e) { "Delayed delete paste task failed for pasteDataId=$pasteDataId" }
                }
            }
        }

        return SuccessPasteTaskResult()
    }
}
