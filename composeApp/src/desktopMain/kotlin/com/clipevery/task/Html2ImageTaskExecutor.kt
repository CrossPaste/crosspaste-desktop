package com.clipevery.task

import com.clipevery.clip.ChromeService
import com.clipevery.clip.item.ClipHtml
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.task.ClipTask
import com.clipevery.dao.task.TaskType
import com.clipevery.presist.FilePersist
import com.clipevery.ui.clip.preview.getClipItem
import com.clipevery.utils.TaskUtils
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
                logger.error(e) { "html 2 image fail" }
                return FailureClipTaskResult(
                    TaskUtils.createFailExtraInfo(clipTask, e),
                )
            }
        }
        return SuccessClipTaskResult()
    }
}
