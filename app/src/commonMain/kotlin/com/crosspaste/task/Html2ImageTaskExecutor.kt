package com.crosspaste.task

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.rendering.RenderingService
import com.crosspaste.utils.StripedMutex
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

class Html2ImageTaskExecutor(
    private val lazyHtmlRenderingService: Lazy<RenderingService<String>>,
    private val pasteDao: PasteDao,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override val taskType: Int = TaskType.HTML_TO_IMAGE_TASK

    private val mutex = StripedMutex()

    private val htmlRenderingServiceDeferred: Deferred<RenderingService<String>> =
        CoroutineScope(ioDispatcher).async {
            lazyHtmlRenderingService.value
        }

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult =
        runCatching {
            pasteTask.pasteDataId?.let { pasteDataId ->
                mutex.withLock(pasteDataId) {
                    val htmlRenderingService = htmlRenderingServiceDeferred.await()
                    pasteDao.getNoDeletePasteData(pasteTask.pasteDataId)?.let { pasteData ->
                        htmlRenderingService.render(pasteData)
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
                            StandardErrorCode.HTML_2_IMAGE_TASK_FAIL,
                            it,
                        ),
                    ),
                extraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class),
            )
        }
}
