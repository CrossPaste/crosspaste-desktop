package com.crosspaste.task

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.image.GenerateImageService
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.rendering.RenderingService
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Rtf2ImageTaskExecutor(
    lazyRtfRenderingService: Lazy<RenderingService<String>>,
    private val generateImageService: GenerateImageService,
    private val pasteDao: PasteDao,
    private val userDataPathProvider: UserDataPathProvider,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override val taskType: Int = TaskType.RTF_TO_IMAGE_TASK

    private val rtfRenderingService = lazyRtfRenderingService.value

    private val mutex = Mutex()

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        return runCatching {
            pasteTask.pasteDataId?.let { pasteDataId ->
                mutex.withLock(pasteDataId) {
                    pasteDao.getNoDeletePasteData(pasteTask.pasteDataId)?.let { pasteData ->
                        val rtf2ImagePath = pasteRtf.getRtfImagePath(userDataPathProvider)
                        if (!fileUtils.existFile(rtf2ImagePath)) {
                            rtfRenderingService.saveRenderImage(pasteRtf.rtf, rtf2ImagePath)
                            generateImageService.getGenerateState(rtf2ImagePath).emit(true)
                        }
                    }
                }
            }
            SuccessPasteTaskResult()
        }.getOrElse {
            TaskUtils.createFailurePasteTaskResult(
                logger = logger,
                retryHandler = { false },
                startTime = pasteTask.modifyTime,
                fails = listOf(createFailureResult(StandardErrorCode.HTML_2_IMAGE_TASK_FAIL, it)),
                extraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class),
            )
        }
    }
}
