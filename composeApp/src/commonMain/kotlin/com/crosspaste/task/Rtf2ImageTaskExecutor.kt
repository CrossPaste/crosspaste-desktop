package com.crosspaste.task

import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.realm.task.BaseExtraInfo
import com.crosspaste.realm.task.PasteTask
import com.crosspaste.realm.task.TaskType
import com.crosspaste.rendering.RenderingService
import com.crosspaste.utils.TaskUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Rtf2ImageTaskExecutor(
    lazyRtfRenderingService: Lazy<RenderingService<String>>,
    private val pasteRealm: PasteRealm,
    private val userDataPathProvider: UserDataPathProvider,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override val taskType: Int = TaskType.RTF_TO_IMAGE_TASK

    private val rtfRenderingService = lazyRtfRenderingService.value

    private val mutex = Mutex()

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        mutex.withLock {
            try {
                pasteRealm.getPasteData(pasteTask.pasteDataId!!)?.let { pasteData ->
                    pasteData.getPasteItem(PasteRtf::class)?.let { pasteRtf ->
                        val rtf2ImagePath = pasteRtf.getRtfImagePath(userDataPathProvider)
                        if (!fileUtils.existFile(rtf2ImagePath)) {
                            rtfRenderingService.saveRenderImage(pasteRtf.rtf, rtf2ImagePath)
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
