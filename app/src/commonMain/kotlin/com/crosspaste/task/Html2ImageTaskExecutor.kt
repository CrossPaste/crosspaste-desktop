package com.crosspaste.task

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.paste.plugin.type.HtmlTypePlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.rendering.RenderingService
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Html2ImageTaskExecutor(
    private val lazyHtmlRenderingService: Lazy<RenderingService<String>>,
    private val pasteDao: PasteDao,
    private val htmlTypePlugin: HtmlTypePlugin,
    private val userDataPathProvider: UserDataPathProvider,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override val taskType: Int = TaskType.HTML_TO_IMAGE_TASK

    private val mutex = Mutex()

    private val htmlRenderingServiceDeferred: Deferred<RenderingService<String>> =
        CoroutineScope(ioDispatcher).async {
            lazyHtmlRenderingService.value
        }

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        mutex.withLock {
            val htmlRenderingService = htmlRenderingServiceDeferred.await()
            try {
                pasteDao.getNoDeletePasteData(pasteTask.pasteDataId!!)?.let { pasteData ->
                    pasteData.getPasteItem(PasteHtml::class)?.let { pasteHtml ->
                        val html2ImagePath = pasteHtml.getHtmlImagePath(userDataPathProvider)
                        if (!fileUtils.existFile(html2ImagePath)) {
                            val normalizeHtml = htmlTypePlugin.normalizeHtml(pasteHtml.html, pasteData.source)
                            htmlRenderingService.saveRenderImage(normalizeHtml, html2ImagePath)
                        }
                    }
                }
            } catch (e: Throwable) {
                return TaskUtils.createFailurePasteTaskResult(
                    logger = logger,
                    retryHandler = { false },
                    startTime = pasteTask.modifyTime,
                    fails = listOf(createFailureResult(StandardErrorCode.HTML_2_IMAGE_TASK_FAIL, e)),
                    extraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class),
                )
            }
        }
        return SuccessPasteTaskResult()
    }
}
