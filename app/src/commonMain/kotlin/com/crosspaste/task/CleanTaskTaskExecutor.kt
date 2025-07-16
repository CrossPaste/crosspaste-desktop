package com.crosspaste.task

import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskDao
import com.crosspaste.db.task.TaskType.CLEAN_TASK_TASK
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.TaskUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

class CleanTaskTaskExecutor(
    private val taskDao: TaskDao,
) : SingleTypeTaskExecutor {

    private val logger = KotlinLogging.logger { }

    override val taskType: Int = CLEAN_TASK_TASK

    @OptIn(ExperimentalTime::class)
    override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult =
        runCatching {
            val twelveHours = 12.hours
            val prev12Hours =
                DateUtils
                    .nowInstant()
                    .minus(twelveHours)
                    .toEpochMilliseconds()

            taskDao.cleanSuccessTask(prev12Hours)

            val threeDays = 3.days

            val prev3Days =
                DateUtils
                    .nowInstant()
                    .minus(threeDays)
                    .toEpochMilliseconds()

            taskDao.cleanFailureTask(prev3Days)

            SuccessPasteTaskResult()
        }.onFailure { e ->
            logger.error(e) { "Error while cleaning task" }
        }.getOrElse {
            val baseExtraInfo = TaskUtils.getExtraInfo(pasteTask, BaseExtraInfo::class)
            TaskUtils.createFailurePasteTaskResult(
                logger = logger,
                retryHandler = { baseExtraInfo.executionHistories.size < 2 },
                startTime = pasteTask.modifyTime,
                fails = listOf(createFailureResult(StandardErrorCode.CLEAN_TASK_FAIL, it)),
                extraInfo = baseExtraInfo,
            )
        }
}
