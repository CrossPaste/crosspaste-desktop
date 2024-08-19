package com.crosspaste.task

import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.task.PasteTask
import com.crosspaste.dao.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.html.ChromeService
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilePersist
import com.crosspaste.task.extra.BaseExtraInfo
import com.crosspaste.ui.paste.preview.getPasteItem
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.TaskUtils.createFailurePasteTaskResult
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Html2ImageTaskExecutor(
    private val lazyChromeService: Lazy<ChromeService>,
    private val pasteDao: PasteDao,
    private val filePersist: FilePersist,
    private val userDataPathProvider: UserDataPathProvider,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override val taskType: Int = TaskType.HTML_TO_IMAGE_TASK

    private val mutex = Mutex()

    private val chromeServiceDeferred: Deferred<ChromeService> =
        CoroutineScope(ioDispatcher).async {
            lazyChromeService.value
        }

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        mutex.withLock {
            val chromeService = chromeServiceDeferred.await()
            try {
                pasteDao.getPasteData(pasteTask.pasteDataId!!)?.let { pasteData ->
                    pasteData.getPasteItem()?.let { pasteItem ->
                        if (pasteItem is PasteHtml) {
                            val html2ImagePath = pasteItem.getHtmlImagePath(userDataPathProvider)
                            if (!html2ImagePath.toFile().exists()) {
                                chromeService.html2Image(pasteItem.html)?.let { bytes ->
                                    filePersist.createOneFilePersist(html2ImagePath).saveBytes(bytes)
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                return createFailurePasteTaskResult(
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
