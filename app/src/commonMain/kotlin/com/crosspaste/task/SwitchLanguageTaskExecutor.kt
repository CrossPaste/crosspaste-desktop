package com.crosspaste.task

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.SwitchLanguageInfo
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.ErrorCodeSupplier
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.GuidePasteDataService
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.TaskUtils
import io.github.oshai.kotlinlogging.KotlinLogging

class SwitchLanguageTaskExecutor(
    private val configManager: CommonConfigManager,
    private val guidePasteDataService: GuidePasteDataService,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger {}

    override val taskType: Int = TaskType.SWITCH_LANGUAGE_TASK

    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
        val switchLanguageInfo = TaskUtils.getExtraInfo(pasteTask, SwitchLanguageInfo::class)
        val currentLanguage = configManager.getCurrentConfig().language

        if (switchLanguageInfo.language != currentLanguage) {
            logger.info {
                "Skip switch to ${switchLanguageInfo.language}, " +
                    "current language is $currentLanguage"
            }
            return SuccessPasteTaskResult()
        }

        return runCatching {
            guidePasteDataService.updateData()
            SuccessPasteTaskResult()
        }.getOrElse { e ->
            createFailurePasteTaskResult(
                switchLanguageInfo = switchLanguageInfo,
                errorMessage = "switch fail: ${e.message}",
            )
        }
    }

    private fun createFailurePasteTaskResult(
        switchLanguageInfo: SwitchLanguageInfo,
        errorCodeSupplier: ErrorCodeSupplier = StandardErrorCode.SWITCH_LANGUAGE_TASK_FAIL,
        errorMessage: String,
    ): PasteTaskResult {
        return TaskUtils.createFailurePasteTaskResult(
            logger = logger,
            retryHandler = { false },
            startTime = nowEpochMilliseconds(),
            fails =
                listOf(
                    createFailureResult(
                        errorCodeSupplier,
                        errorMessage,
                    ),
                ),
            extraInfo = switchLanguageInfo,
        )
    }
}
