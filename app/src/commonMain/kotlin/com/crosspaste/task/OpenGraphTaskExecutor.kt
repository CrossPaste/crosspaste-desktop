package com.crosspaste.task

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.rendering.RenderingService
import com.crosspaste.utils.TaskUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OpenGraphTaskExecutor(
    lazyUrlRenderingService: Lazy<RenderingService<String>>,
    private val pasteDao: PasteDao,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override val taskType: Int = TaskType.OPEN_GRAPH_TASK

    private val urlRenderingService = lazyUrlRenderingService.value

    private val mutex = Mutex()

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        return runCatching {
            pasteTask.pasteDataId?.let { pasteDataId ->
                mutex.withLock(pasteDataId) {
                    pasteDao.getNoDeletePasteData(pasteTask.pasteDataId)?.let { pasteData ->
                        urlRenderingService.render(pasteData)
                    }
                }
            }
            SuccessPasteTaskResult()
        }.getOrElse {
            TaskUtils.createFailurePasteTaskResult(
                logger = logger,
                retryHandler = { false },
                startTime = pasteTask.modifyTime,
                fails =
                    listOf(
                        createFailureResult(
                            StandardErrorCode.OPEN_GRAPH_TASK_FAIL,
                            it,
                        ),
                    ),
                extraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class),
            )
        }
    }
}
