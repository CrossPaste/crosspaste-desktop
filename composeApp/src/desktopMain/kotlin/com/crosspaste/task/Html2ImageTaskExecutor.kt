package com.crosspaste.task

import com.crosspaste.clip.ChromeService
import com.crosspaste.clip.item.ClipHtml
import com.crosspaste.dao.clip.ClipDao
import com.crosspaste.dao.task.ClipTask
import com.crosspaste.dao.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.presist.FilePersist
import com.crosspaste.task.extra.BaseExtraInfo
import com.crosspaste.ui.clip.preview.getClipItem
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.TaskUtils.createFailureClipTaskResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Html2ImageTaskExecutor(
    private val chromeService: ChromeService,
    private val clipDao: ClipDao,
    private val filePersist: FilePersist,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override val taskType: Int = TaskType.HTML_TO_IMAGE_TASK

    private val mutex = Mutex()

    override suspend fun doExecuteTask(clipTask: ClipTask): ClipTaskResult {
        mutex.withLock {
            try {
                clipDao.getClipData(clipTask.clipDataId!!)?.let { clipData ->
                    clipData.getClipItem()?.let { clipItem ->
                        if (clipItem is ClipHtml) {
                            val html2ImagePath = clipItem.getHtmlImagePath()
                            if (!html2ImagePath.toFile().exists()) {
                                chromeService.html2Image(clipItem.html)?.let { bytes ->
                                    filePersist.createOneFilePersist(html2ImagePath).saveBytes(bytes)
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                return createFailureClipTaskResult(
                    logger = logger,
                    retryHandler = { false },
                    startTime = clipTask.modifyTime,
                    fails = listOf(createFailureResult(StandardErrorCode.HTML_2_IMAGE_TASK_FAIL, e)),
                    extraInfo = TaskUtils.getExtraInfo(clipTask, BaseExtraInfo::class),
                )
            }
        }
        return SuccessClipTaskResult()
    }
}
